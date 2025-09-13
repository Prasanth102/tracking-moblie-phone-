from flask import Flask, request, jsonify
import sqlite3, os, time

app = Flask(__name__)
DB = "locations.db"

def init_db():
    if not os.path.exists(DB):
        conn = sqlite3.connect(DB)
        c = conn.cursor()
        c.execute("""
            CREATE TABLE reports(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                deviceId TEXT,
                lat REAL, lon REAL,
                accuracy REAL,
                timestamp INTEGER
            )
        """)
        conn.commit()
        conn.close()

@app.route("/report", methods=["POST"])
def report():
    data = request.json
    if not data: return {"error": "no data"}, 400
    conn = sqlite3.connect(DB)
    c = conn.cursor()
    c.execute("INSERT INTO reports(deviceId, lat, lon, accuracy, timestamp) VALUES(?,?,?,?,?)",
              (data["deviceId"], data["lat"], data["lon"], data["accuracy"], data["timestamp"]))
    conn.commit()
    conn.close()
    return {"status": "ok"}

@app.route("/latest/<device>")
def latest(device):
    conn = sqlite3.connect(DB)
    c = conn.cursor()
    c.execute("SELECT lat, lon, timestamp FROM reports WHERE deviceId=? ORDER BY timestamp DESC LIMIT 1", (device,))
    row = c.fetchone()
    conn.close()
    if not row:
        return {"status": "not_found"}, 404
    return {"device": device, "lat": row[0], "lon": row[1], "timestamp": row[2]}

if __name__ == "__main__":
    init_db()
    app.run(host="0.0.0.0", port=5000, debug=True)
