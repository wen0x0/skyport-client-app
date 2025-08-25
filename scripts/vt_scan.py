import requests
import time
import tkinter as tk
from tkinter import filedialog
import os

API_KEY = "7d8d6261bb16997980f72074ac9e7bfe9829c46dc2e3e20f36c14aefd6bb1554"  

def pick_file():
    root = tk.Tk()
    root.withdraw()  
    file_path = filedialog.askopenfilename(title="Select a file to scan")
    return file_path

def scan_file(file_path):
    headers = {"x-apikey": API_KEY}

    # Check file size (limit 32 MB for direct upload)
    if os.path.getsize(file_path) > 32 * 1024 * 1024:
        r = requests.get("https://www.virustotal.com/api/v3/files/upload_url", headers=headers)
        upload_url = r.json()["data"]
    else:
        upload_url = "https://www.virustotal.com/api/v3/files"

    # Upload file
    with open(file_path, "rb") as f:
        files = {"file": (os.path.basename(file_path), f)}
        resp = requests.post(upload_url, headers=headers, files=files)
    resp.raise_for_status()
    analysis_id = resp.json()["data"]["id"]

    print(f"Analyzing file: {os.path.basename(file_path)}")

    while True:
        r = requests.get(f"https://www.virustotal.com/api/v3/analyses/{analysis_id}", headers=headers)
        r.raise_for_status()
        data = r.json()["data"]["attributes"]
        if data["status"] == "completed":
            stats = data["stats"]
            print("Scan results:")
            print("  Malicious :", stats.get("malicious", 0))
            print("  Suspicious:", stats.get("suspicious", 0))
            print("  Harmless  :", stats.get("harmless", 0))
            print("  Undetected:", stats.get("undetected", 0))

            if stats.get("malicious", 0) > 0 or stats.get("suspicious", 0) > 0:
                print("WARNING: This file may be MALWARE!")
            else:
                print("The file appears to be safe.")
            break
        else:
            print("Still analyzing... waiting 5 seconds")
            time.sleep(5)

if __name__ == "__main__":
    file = pick_file()
    if file:
        scan_file(file)
    else:
        print("No file selected.")
