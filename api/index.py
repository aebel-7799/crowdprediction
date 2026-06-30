import sys
import os

# Add the project root and backend folder to sys.path so imports work
project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.append(project_root)
sys.path.append(os.path.join(project_root, "backend"))

# Import the Flask application instance
from app import app
