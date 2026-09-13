import subprocess
import xml.etree.ElementTree as ET
import time
import re

def main():
    subprocess.run(["adb", "shell", "input", "keyevent", "4"])
    time.sleep(1)
    subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/h.xml"])
    subprocess.run(["adb", "pull", "/sdcard/h.xml", "h.xml"])
    tree = ET.parse("h.xml")
    root = tree.getroot()
    btn_menu_bounds = None
    for elem in root.iter():
        res_id = elem.attrib.get("resource-id", "")
        bounds = elem.attrib.get("bounds", "")
        if "btnTopMenu" in res_id:
            print(f"Found btnTopMenu: {bounds}")
            btn_menu_bounds = bounds
        elif "btn" in res_id:
            print(f"{res_id}: {bounds}")
    
    if btn_menu_bounds:
        m = re.findall(r"\[(\d+),(\d+)\]", btn_menu_bounds)
        if m:
            x1, y1 = int(m[0][0]), int(m[0][1])
            x2, y2 = int(m[1][0]), int(m[1][1])
            cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
            print(f"Tapping btnTopMenu at {cx}, {cy}")
            subprocess.run(["adb", "shell", "input", "tap", str(cx), str(cy)])
            time.sleep(2)
            # Take screenshot of drawer
            subprocess.run(["adb", "shell", "screencap", "-p", "/sdcard/drawer_open.png"])
            subprocess.run(["adb", "pull", "/sdcard/drawer_open.png", "drawer_open.png"])
            print("Captured drawer_open.png")

if __name__ == "__main__":
    main()
