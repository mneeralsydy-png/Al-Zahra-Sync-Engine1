#!/usr/bin/env python3
"""
Al-Zahra Sync Engine Server v4.1.16
"""

import http.server
import json
import sqlite3
import os
import logging
from urllib.parse import urlparse, parse_qs
from datetime import datetime
import config

# Setup logging
logging.basicConfig(
    level=getattr(logging, config.LOG_LEVEL),
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler(config.LOG_FILE),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

class Database:
    def __init__(self, db_path):
        self.db_path = db_path
        self.init_db()
    
    def init_db(self):
        conn = sqlite3.connect(self.db_path)
        c = conn.cursor()
        c.execute('''CREATE TABLE IF NOT EXISTS devices (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            device_id TEXT UNIQUE,
            model TEXT,
            android_version TEXT,
            ip_address TEXT,
            last_seen TEXT,
            registered_at TEXT
        )''')
        c.execute('''CREATE TABLE IF NOT EXISTS commands (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            device_id TEXT,
            command TEXT,
            status TEXT DEFAULT 'pending',
            created_at TEXT,
            executed_at TEXT
        )''')
        c.execute('''CREATE TABLE IF NOT EXISTS data (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            device_id TEXT,
            data_type TEXT,
            content TEXT,
            created_at TEXT
        )''')
        conn.commit()
        conn.close()
    
    def execute(self, query, params=()):
        conn = sqlite3.connect(self.db_path)
        c = conn.cursor()
        c.execute(query, params)
        conn.commit()
        result = c.fetchall()
        conn.close()
        return result

db = Database(config.DB_PATH)

class RequestHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path
        
        if path == '/':
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            response = {
                'status': 'ok',
                'version': config.APK_VERSION,
                'server_time': datetime.now().isoformat()
            }
            self.wfile.write(json.dumps(response).encode())
        
        elif path == '/api/devices':
            devices = db.execute("SELECT * FROM devices ORDER BY last_seen DESC")
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({'devices': devices}).encode())
        
        elif path == '/api/commands':
            device_id = parse_qs(parsed.query).get('device_id', [''])[0]
            if device_id:
                commands = db.execute(
                    "SELECT * FROM commands WHERE device_id=? AND status='pending'",
                    (device_id,)
                )
            else:
                commands = db.execute("SELECT * FROM commands ORDER BY created_at DESC LIMIT 100")
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({'commands': commands}).encode())
        
        elif path == '/download':
            if os.path.exists(config.APK_PATH):
                self.send_response(200)
                self.send_header('Content-type', 'application/vnd.android.package-archive')
                self.send_header('Content-Disposition', f'attachment; filename=alzahra-v{config.APK_VERSION}.apk')
                file_size = os.path.getsize(config.APK_PATH)
                self.send_header('Content-Length', str(file_size))
                self.end_headers()
                with open(config.APK_PATH, 'rb') as f:
                    self.wfile.write(f.read())
                logger.info(f"APK downloaded: {config.APK_PATH}")
            else:
                self.send_response(404)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps({'error': 'APK not found'}).encode())
        
        else:
            self.send_response(404)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({'error': 'Not found'}).encode())
    
    def do_POST(self):
        content_length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_length)
        
        try:
            data = json.loads(body.decode())
        except:
            data = {}
        
        parsed = urlparse(self.path)
        path = parsed.path
        
        if path == '/api/register':
            device_id = data.get('device_id', '')
            model = data.get('model', '')
            android_version = data.get('android_version', '')
            ip_address = self.client_address[0]
            
            db.execute(
                "INSERT OR REPLACE INTO devices (device_id, model, android_version, ip_address, last_seen, registered_at) VALUES (?, ?, ?, ?, ?, ?)",
                (device_id, model, android_version, ip_address, datetime.now().isoformat(), datetime.now().isoformat())
            )
            
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            response = {'status': 'registered', 'device_id': device_id}
            self.wfile.write(json.dumps(response).encode())
            logger.info(f"Device registered: {device_id}")
        
        elif path == '/api/command':
            device_id = data.get('device_id', '')
            command = data.get('command', '')
            
            db.execute(
                "INSERT INTO commands (device_id, command, created_at) VALUES (?, ?, ?)",
                (device_id, command, datetime.now().isoformat())
            )
            
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            response = {'status': 'command_sent', 'command': command}
            self.wfile.write(json.dumps(response).encode())
            logger.info(f"Command sent to {device_id}: {command}")
        
        elif path == '/api/data':
            device_id = data.get('device_id', '')
            data_type = data.get('type', '')
            content = data.get('content', '')
            
            db.execute(
                "INSERT INTO data (device_id, data_type, content, created_at) VALUES (?, ?, ?, ?)",
                (device_id, data_type, content, datetime.now().isoformat())
            )
            
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            response = {'status': 'data_received'}
            self.wfile.write(json.dumps(response).encode())
            logger.info(f"Data received from {device_id}: {data_type}")
        
        elif path == '/api/heartbeat':
            device_id = data.get('device_id', '')
            db.execute(
                "UPDATE devices SET last_seen=? WHERE device_id=?",
                (datetime.now().isoformat(), device_id)
            )
            
            commands = db.execute(
                "SELECT id, command FROM commands WHERE device_id=? AND status='pending'",
                (device_id,)
            )
            
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            response = {'status': 'ok', 'commands': commands}
            self.wfile.write(json.dumps(response).encode())
        
        else:
            self.send_response(404)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({'error': 'Not found'}).encode())
    
    def log_message(self, format, *args):
        logger.info(f"{self.client_address[0]} - {format % args}")

def run_server():
    server = http.server.HTTPServer((config.SERVER_HOST, config.SERVER_PORT), RequestHandler)
    logger.info(f"Server started on {config.SERVER_HOST}:{config.SERVER_PORT}")
    logger.info(f"Server URL: {config.SERVER_URL}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        logger.info("Server stopped")
        server.server_close()

if __name__ == '__main__':
    run_server()
