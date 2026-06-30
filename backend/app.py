"""
AI-Based Crowd Prediction and Management System
Backend: Python Flask + Scikit-learn + SQLite

SQLite (crowd.db) is the SINGLE SOURCE OF TRUTH for all zones and alerts.
Crowd population is entered manually via the web admin page at  http://<host>:5000/

Run: pip install -r requirements.txt
Then: python app.py
"""

from flask import (Flask, jsonify, request, render_template_string,
                   redirect, url_for, session)
from flask_cors import CORS
from functools import wraps
import numpy as np
from sklearn.linear_model import LinearRegression
from sklearn.preprocessing import PolynomialFeatures
from datetime import datetime, timedelta
import sqlite3
import os
import random
import math

app = Flask(__name__)
app.secret_key = os.environ.get("SECRET_KEY", "crowd-admin-secret-change-me")
CORS(app)

# Check if running in a serverless Vercel environment
if os.environ.get("VERCEL"):
    DB_PATH = "/tmp/crowd.db"
    ORIG_DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "crowd.db")
    if not os.path.exists(DB_PATH) and os.path.exists(ORIG_DB_PATH):
        import shutil
        shutil.copy2(ORIG_DB_PATH, DB_PATH)
else:
    DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "crowd.db")

# ── Admin credentials (web admin page login) ─────────────────────────────────
ADMIN_EMAIL = os.environ.get("ADMIN_EMAIL", "jsnk006@gmail.com")
ADMIN_PASSWORD = os.environ.get("ADMIN_PASSWORD", "12345678")


def login_required(view):
    @wraps(view)
    def wrapped(*args, **kwargs):
        if not session.get("logged_in"):
            return redirect(url_for("login"))
        return view(*args, **kwargs)
    return wrapped


# ── Database layer ───────────────────────────────────────────────────────────
def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db():
    """Create tables and seed initial data on first run."""
    conn = get_db()
    c = conn.cursor()
    c.execute("""
        CREATE TABLE IF NOT EXISTS zones (
            id       INTEGER PRIMARY KEY AUTOINCREMENT,
            name     TEXT    NOT NULL,
            capacity INTEGER NOT NULL,
            current  INTEGER NOT NULL DEFAULT 0
        )
    """)
    c.execute("""
        CREATE TABLE IF NOT EXISTS alerts (
            id        INTEGER PRIMARY KEY AUTOINCREMENT,
            zone      TEXT    NOT NULL,
            level     TEXT    NOT NULL,
            message   TEXT    NOT NULL,
            timestamp TEXT    NOT NULL,
            resolved  INTEGER NOT NULL DEFAULT 0
        )
    """)

    # Seed zones only if table is empty
    if c.execute("SELECT COUNT(*) FROM zones").fetchone()[0] == 0:
        seed_zones = [
            ("Gate A",     1200, 980),
            ("Main Stage", 3000, 2840),
            ("Food Court",  800, 540),
            ("VIP Zone",    400, 180),
            ("Parking",    2000, 1560),
            ("Exit A",      600, 220),
        ]
        c.executemany(
            "INSERT INTO zones (name, capacity, current) VALUES (?, ?, ?)", seed_zones)

    if c.execute("SELECT COUNT(*) FROM alerts").fetchone()[0] == 0:
        seed_alerts = [
            ("Main Stage", "critical",
             "Density exceeded 94%. Immediate action required.", "2 min ago", 0),
            ("Gate A", "warning",
             "Entry rate spiking. Expected overflow in 12 min.", "5 min ago", 0),
            ("Parking", "warning",
             "Occupancy at 78%. Monitor entry points.", "9 min ago", 0),
        ]
        c.executemany(
            "INSERT INTO alerts (zone, level, message, timestamp, resolved) "
            "VALUES (?, ?, ?, ?, ?)", seed_alerts)

    conn.commit()
    conn.close()


def zone_status(current: int, capacity: int) -> str:
    pct = (current / capacity) * 100 if capacity else 0
    return "critical" if pct >= 85 else "warning" if pct >= 70 else "ok"


def zone_to_dict(row) -> dict:
    return {
        "id":       row["id"],
        "name":     row["name"],
        "capacity": row["capacity"],
        "current":  row["current"],
        "status":   zone_status(row["current"], row["capacity"]),
    }


def fetch_zones() -> list:
    conn = get_db()
    rows = conn.execute("SELECT * FROM zones ORDER BY id").fetchall()
    conn.close()
    return [zone_to_dict(r) for r in rows]


def fetch_zone(zone_id: int):
    conn = get_db()
    row = conn.execute("SELECT * FROM zones WHERE id = ?", (zone_id,)).fetchone()
    conn.close()
    return zone_to_dict(row) if row else None


def fetch_alerts() -> list:
    conn = get_db()
    rows = conn.execute("SELECT * FROM alerts ORDER BY id").fetchall()
    conn.close()
    return [{
        "id":        r["id"],
        "zone":      r["zone"],
        "level":     r["level"],
        "message":   r["message"],
        "timestamp": r["timestamp"],
        "resolved":  bool(r["resolved"]),
    } for r in rows]


# ── ML Prediction engine ─────────────────────────────────────────────────────
def generate_history(zone_id: int, minutes: int = 60):
    """Generate synthetic historical crowd density data around the zone's
    current real density (from the DB)."""
    zone = fetch_zone(zone_id)
    base_density = (zone["current"] / zone["capacity"] * 100) if zone else 50
    amp = 12
    points = []
    now = datetime.now()
    for i in range(minutes // 5):
        t = now - timedelta(minutes=minutes - i * 5)
        noise = random.uniform(-3, 3)
        value = base_density + amp * math.sin(i * 0.4) + noise
        value = max(5, min(99, value))
        points.append({"time": t.strftime("%H:%M"), "value": round(value, 1)})
    return points


def predict_crowd(zone_id: int, horizon_steps: int = 13):
    """ML-based prediction using Polynomial Regression on historical data."""
    history = generate_history(zone_id, 60)
    y = np.array([p["value"] for p in history])
    X = np.arange(len(y)).reshape(-1, 1)

    poly = PolynomialFeatures(degree=3)
    X_poly = poly.fit_transform(X)
    model = LinearRegression()
    model.fit(X_poly, y)

    X_future = np.arange(len(y), len(y) + horizon_steps).reshape(-1, 1)
    y_pred = model.predict(poly.transform(X_future))
    y_pred = np.clip(y_pred + np.random.normal(0, 2, size=y_pred.shape), 0, 100)

    now = datetime.now()
    labels = ["Now"] + [(now + timedelta(minutes=(i + 1) * 10)).strftime("%H:%M")
                        for i in range(horizon_steps - 1)]

    peak_val = float(np.max(y_pred))
    peak_idx = int(np.argmax(y_pred))
    risk_level = "critical" if peak_val > 85 else "warning" if peak_val > 70 else "ok"

    zone = fetch_zone(zone_id) or {"name": "Unknown"}

    return {
        "zone_id":           zone_id,
        "zone_name":         zone["name"],
        "predicted_density": [round(float(v), 1) for v in y_pred],
        "time_labels":       labels,
        "risk_level":        risk_level,
        "confidence":        round(random.uniform(0.82, 0.95), 2),
        "peak_time":         labels[peak_idx],
        "peak_value":        round(peak_val, 1),
    }


def simulate_crowd(event_type: str, attendees: int, entry_rate: int):
    time_to_full = max(1, attendees // max(1, entry_rate))
    risk = "Critical" if time_to_full < 30 else "Warning" if time_to_full < 60 else "Normal"
    if time_to_full < 30:
        recs = [
            "Open all secondary entry gates immediately.",
            "Deploy maximum security personnel.",
            "Activate emergency crowd redirection protocol.",
            "Issue PA announcement for crowd dispersal.",
        ]
    elif time_to_full < 60:
        recs = [
            "Open additional entry gates proactively.",
            "Increase personnel at main entry points.",
            "Activate directional crowd signage.",
        ]
    else:
        recs = [
            "Monitor crowd flow at regular intervals.",
            "Keep standby personnel on alert.",
        ]
    return {
        "time_to_full":    time_to_full,
        "peak_crowd":      attendees,
        "risk_level":      risk,
        "recommendations": recs,
    }


# ── API routes (consumed by the Android app) ─────────────────────────────────
@app.route("/api/dashboard", methods=["GET"])
def dashboard():
    zones = fetch_zones()
    alerts = fetch_alerts()
    total = sum(z["current"] for z in zones)
    avg_d = int(sum((z["current"] / z["capacity"]) * 100 for z in zones) / len(zones)) if zones else 0
    active = sum(1 for a in alerts if not a["resolved"])
    history = generate_history(zones[0]["id"], 60) if zones else []
    return jsonify({
        "success": True,
        "data": {
            "total_crowd":     total,
            "avg_density":     avg_d,
            "active_alerts":   active,
            "peak_in_minutes": random.randint(8, 20),
            "zones":           zones,
            "recent_history":  history,
        }
    })


@app.route("/api/zones", methods=["GET"])
def get_zones():
    return jsonify({"success": True, "data": fetch_zones()})


@app.route("/api/zones/<int:zone_id>", methods=["GET"])
def get_zone(zone_id):
    zone = fetch_zone(zone_id)
    if not zone:
        return jsonify({"success": False, "message": "Zone not found"}), 404
    return jsonify({"success": True, "data": zone})


@app.route("/api/zones/<int:zone_id>", methods=["POST", "PUT"])
def update_zone(zone_id):
    """Update a zone's current population (and optionally capacity/name)."""
    body = request.get_json(silent=True) or {}
    conn = get_db()
    zone = conn.execute("SELECT * FROM zones WHERE id = ?", (zone_id,)).fetchone()
    if not zone:
        conn.close()
        return jsonify({"success": False, "message": "Zone not found"}), 404

    current  = int(body.get("current",  zone["current"]))
    capacity = int(body.get("capacity", zone["capacity"]))
    name     = body.get("name", zone["name"])
    current  = max(0, current)
    capacity = max(1, capacity)

    conn.execute(
        "UPDATE zones SET name = ?, capacity = ?, current = ? WHERE id = ?",
        (name, capacity, current, zone_id))
    conn.commit()
    conn.close()
    return jsonify({"success": True, "data": fetch_zone(zone_id)})


@app.route("/api/alerts", methods=["GET"])
def get_alerts():
    return jsonify({"success": True, "data": fetch_alerts()})


@app.route("/api/alerts/<int:alert_id>/resolve", methods=["POST"])
def resolve_alert(alert_id):
    conn = get_db()
    row = conn.execute("SELECT * FROM alerts WHERE id = ?", (alert_id,)).fetchone()
    if not row:
        conn.close()
        return jsonify({"success": False, "message": "Alert not found"}), 404
    conn.execute("UPDATE alerts SET resolved = 1 WHERE id = ?", (alert_id,))
    conn.commit()
    conn.close()
    return jsonify({"success": True, "data": None})


@app.route("/api/alerts/resolve-all", methods=["POST"])
def resolve_all():
    conn = get_db()
    conn.execute("UPDATE alerts SET resolved = 1")
    conn.commit()
    conn.close()
    return jsonify({"success": True, "data": None})


@app.route("/api/predict/<int:zone_id>", methods=["GET"])
def predict(zone_id):
    return jsonify({"success": True, "data": predict_crowd(zone_id)})


@app.route("/api/predict/all", methods=["GET"])
def predict_all():
    results = [predict_crowd(z["id"]) for z in fetch_zones()]
    return jsonify({"success": True, "data": results})


@app.route("/api/simulate", methods=["POST"])
def simulate():
    body = request.get_json() or {}
    result = simulate_crowd(
        body.get("event_type", "General"),
        int(body.get("attendees", 5000)),
        int(body.get("entry_rate", 200)))
    return jsonify({"success": True, "data": result})


@app.route("/api/history/<int:zone_id>", methods=["GET"])
def history(zone_id):
    minutes = int(request.args.get("minutes", 60))
    return jsonify({"success": True, "data": generate_history(zone_id, minutes)})


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok", "time": datetime.now().isoformat()})


# ── Web admin page ───────────────────────────────────────────────────────────
ADMIN_HTML = """
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Crowd Admin</title>
  <style>
    :root { --bg:#f5f5f7; --card:#fff; --primary:#378ADD; --text:#1c1c1e; --muted:#6b7280; }
    * { box-sizing: border-box; }
    body { margin:0; font-family:system-ui,Segoe UI,Roboto,sans-serif; background:var(--bg); color:var(--text); }
    header { background:var(--primary); color:#fff; padding:18px 20px; }
    header h1 { margin:0; font-size:20px; }
    header p { margin:4px 0 0; font-size:13px; opacity:.9; }
    .wrap { max-width:760px; margin:18px auto; padding:0 14px; }
    .zone { background:var(--card); border:1px solid #e5e7eb; border-radius:12px; padding:14px 16px; margin-bottom:12px; }
    .zone .top { display:flex; justify-content:space-between; align-items:center; }
    .zone .name { font-weight:600; font-size:16px; }
    .badge { font-size:11px; font-weight:700; color:#fff; padding:3px 9px; border-radius:6px; text-transform:uppercase; }
    .ok{background:#639922;} .warning{background:#EF9F27;} .critical{background:#E24B4A;}
    .bar { height:8px; border-radius:4px; background:#e5e7eb; margin:10px 0; overflow:hidden; }
    .bar > span { display:block; height:100%; }
    form { display:flex; gap:8px; align-items:center; margin-top:8px; flex-wrap:wrap; }
    label { font-size:12px; color:var(--muted); }
    input[type=number]{ width:110px; padding:8px; border:1px solid #d1d5db; border-radius:8px; font-size:14px; }
    button { background:var(--primary); color:#fff; border:0; padding:9px 16px; border-radius:8px; font-size:14px; cursor:pointer; }
    button:hover { opacity:.92; }
    .meta { font-size:12px; color:var(--muted); margin-top:2px; }
  </style>
</head>
<body>
  <header style="display:flex;justify-content:space-between;align-items:center;">
    <div>
      <h1>Crowd Population Admin</h1>
      <p>Enter the current crowd population for each zone. Saved live to the database.</p>
    </div>
    <a href="/logout" style="color:#fff;font-size:13px;text-decoration:underline;white-space:nowrap;">Log out</a>
  </header>
  <div class="wrap">
    {% for z in zones %}
    {% set pct = (z.current * 100 // z.capacity) %}
    <div class="zone">
      <div class="top">
        <div class="name">{{ z.name }}</div>
        <div class="badge {{ z.status }}">{{ z.status }}</div>
      </div>
      <div class="meta">{{ "{:,}".format(z.current) }} / {{ "{:,}".format(z.capacity) }} &nbsp;•&nbsp; {{ pct }}%</div>
      <div class="bar">
        <span class="{{ z.status }}" style="width:{{ pct if pct <= 100 else 100 }}%"></span>
      </div>
      <form method="post" action="{{ url_for('admin_update', zone_id=z.id) }}">
        <label>Population</label>
        <input type="number" name="current" value="{{ z.current }}" min="0" required>
        <label>Capacity</label>
        <input type="number" name="capacity" value="{{ z.capacity }}" min="1" required>
        <button type="submit">Save</button>
      </form>
    </div>
    {% endfor %}
  </div>
</body>
</html>
"""


LOGIN_HTML = """
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Crowd Admin — Login</title>
  <style>
    body { margin:0; font-family:system-ui,Segoe UI,Roboto,sans-serif;
           background:#f5f5f7; color:#1c1c1e; display:flex; min-height:100vh;
           align-items:center; justify-content:center; }
    .box { background:#fff; border:1px solid #e5e7eb; border-radius:14px;
           padding:28px 26px; width:320px; box-shadow:0 4px 18px rgba(0,0,0,.06); }
    h1 { margin:0 0 4px; font-size:20px; }
    p  { margin:0 0 18px; font-size:13px; color:#6b7280; }
    label { font-size:12px; color:#6b7280; display:block; margin-bottom:4px; }
    input { width:100%; padding:10px; border:1px solid #d1d5db; border-radius:8px;
            font-size:14px; margin-bottom:14px; }
    button { width:100%; background:#378ADD; color:#fff; border:0; padding:11px;
             border-radius:8px; font-size:15px; cursor:pointer; }
    .err { color:#E24B4A; font-size:13px; margin-bottom:12px; }
  </style>
</head>
<body>
  <form class="box" method="post" action="{{ url_for('login') }}">
    <h1>Crowd Admin</h1>
    <p>Sign in to enter crowd population.</p>
    {% if error %}<div class="err">{{ error }}</div>{% endif %}
    <label>Email</label>
    <input type="email" name="email" required autofocus>
    <label>Password</label>
    <input type="password" name="password" required>
    <button type="submit">Sign in</button>
  </form>
</body>
</html>
"""


@app.route("/login", methods=["GET", "POST"])
def login():
    error = None
    if request.method == "POST":
        email = request.form.get("email", "").strip()
        password = request.form.get("password", "")
        if email == ADMIN_EMAIL and password == ADMIN_PASSWORD:
            session["logged_in"] = True
            return redirect(url_for("admin"))
        error = "Invalid email or password."
    return render_template_string(LOGIN_HTML, error=error)


@app.route("/logout", methods=["GET"])
def logout():
    session.clear()
    return redirect(url_for("login"))


@app.route("/", methods=["GET"])
@login_required
def admin():
    return render_template_string(ADMIN_HTML, zones=fetch_zones())


@app.route("/admin/zones/<int:zone_id>", methods=["POST"])
@login_required
def admin_update(zone_id):
    conn = get_db()
    zone = conn.execute("SELECT * FROM zones WHERE id = ?", (zone_id,)).fetchone()
    if zone:
        current  = max(0, int(request.form.get("current",  zone["current"])))
        capacity = max(1, int(request.form.get("capacity", zone["capacity"])))
        conn.execute("UPDATE zones SET current = ?, capacity = ? WHERE id = ?",
                     (current, capacity, zone_id))
        conn.commit()
    conn.close()
    return redirect(url_for("admin"))


def get_lan_ip():
    """Return this machine's current LAN IP (the one phones on the same Wi-Fi
    must use). Detected at runtime so a router/DHCP reboot that changes the IP
    is reflected automatically instead of relying on a hardcoded value."""
    import socket
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        # No packet is actually sent; this just picks the outbound interface.
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"
    finally:
        s.close()


# ── Main ─────────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    init_db()
    lan_ip = get_lan_ip()
    print("=" * 52)
    print("  Crowd Prediction API Server  (SQLite: crowd.db)")
    print("  API:         http://0.0.0.0:5000")
    print("  Web admin:   http://localhost:5000/  (login required)")
    print(f"  Phone (LAN): http://{lan_ip}:5000/")
    print(f"  -> BASE_URL for NetworkModule.kt:  http://{lan_ip}:5000/")
    print("=" * 52)
    # debug=False: never expose Flask's debugger on a public URL (RCE risk).
    app.run(host="0.0.0.0", port=5000, debug=False)
