import requests
import json
import urllib3
urllib3.disable_warnings()

headers_template = {
    "Connection": "Keep-Alive",
    "Content-Type": "application/x-www-form-urlencoded",
    "User-Agent": "okhttp/5.5.0",
    "X-App-Signature": "AB20bZUC4SQcU/qhMDVfHWX+1liYELEI512YdfJAe+U=",
    "X-Device-Type": "0",
    "X-Device-Uniq": "8f66ec3b-a880-4267-b287-74c6f841a2cf",
    "X-Key-Password": "8edace647f7270c595cdadb594b450d6a2158a91f12aec20ce9b24d5fd59fa39b06537cee4831daf2f8d58d84b",
    "X-Key-Token": "23606005bfde651bd6118fc638121f99148bd81849d2abff082bf13c998e8ffb794cdf608bb0f48aeec5cd312f2535c8891a7ad727b202d7278ad590ade86eeb",
    "X-Key-Username": "0742ae2df5029929b81c9ce3b09151",
    "X-Package-Name": "com.expertapp.movielixmedia",
    "X-Version-Code": "84",
    "X-Version-Name": "1.8.4"
}

def test_api():
    print("1. Fetching updated Base URL...")
    r = requests.post("https://global-api2.expertmedias.org/apiMovielix.php", data={"movielix": "movielix"}, verify=False)
    print("Base URL response:", r.status_code, r.text)
    base_info = r.json()
    base_url = base_info.get("url", "https://expertappmedia.org/").rstrip("/") + "/api-v1"
    print("Resolved base_url:", base_url)

    host = base_url.split("//")[1].split("/")[0]
    headers = dict(headers_template)
    headers["Host"] = host

    print("\n2. Requesting device version & token (/device/version)...")
    deviceData = {
        'apk_code': '84',
        'apk_name': '1.8.4',
        'device_version': '9',
        'device_model': 'G576D',
        'device_brand': 'Google Phone',
        'device_api': '28',
        'package': 'com.expertapp.movielixmedia',
        'uniq': '8f66ec3b-a880-4267-b287-74c6f841a2cf',
        'type': '0',
        'token_firebase': 'cDgipEqUSCq6-zVcj4LEwH:APA91bFsMIW63cV4Hs6AytZ42gsmgRg2YIVp8EA4wpvSqVrWwvrTQUoP6LkEtjNNeBmrdH08601xlIDGJT_RElRVkG8AR78lMPcFQNpozu8DR3aUk-P4q9g',
        'language': '0',
        'market_type': '0',
        'user_id': '0',
        'mcc': '208',
        'time_zone': 'europe/paris'
    }
    r2 = requests.post(f"{base_url}/device/version", data=deviceData, headers=headers, verify=False)
    print("Device version response:", r2.status_code, r2.text[:200])
    token = r2.json().get("token", "178865496596627099")
    print("Acquired token:", token)

    print("\n3. Handshakes (/account/guest and /language/set)...")
    r3 = requests.post(f"{base_url}/account/guest", data={"token": token, "timezone": "europe/paris", "mcc": "208"}, headers=headers, verify=False)
    print("Guest handshake:", r3.status_code, r3.text[:100])
    r4 = requests.post(f"{base_url}/language/set", data={"token": token, "language": "1"}, headers=headers, verify=False)
    print("Language handshake:", r4.status_code, r4.text[:100])

    print("\n4. Testing /home/main-page...")
    main_page_data = {
        "type": "0",
        "action": "0",
        "genre_id": "-1",
        "page": "1",
        "token": token
    }
    r5 = requests.post(f"{base_url}/home/main-page", data=main_page_data, headers=headers, verify=False)
    print("Main page response code:", r5.status_code)
    print("Main page headers:", r5.headers.get("content-encoding"))
    try:
        j = r5.json()
        print("Success! Sliders count:", len(j.get("slider", [])), "Categories count:", len(j.get("category", {}).get("data", [])))
        if j.get("slider"):
            print("First banner:", j["slider"][0].get("name") or j["slider"][0].get("title"))
    except Exception as e:
        print("JSON parse error:", e, "Raw text:", r5.text[:300])

    print("\n5. Testing /movie/search...")
    search_data = {
        "type": "2",
        "page": "1",
        "value": "جوکر",
        "priority": "6",
        "language_id": "0",
        "token": token
    }
    r6 = requests.post(f"{base_url}/movie/search", data=search_data, headers=headers, verify=False)
    print("Search status:", r6.status_code)
    try:
        j6 = r6.json()
        info_data = j6.get("info", {}).get("data", []) or j6.get("data", [])
        print("Search results count:", len(info_data))
        if info_data:
            print("First item:", info_data[0].get("title") or info_data[0].get("name"))
            movie_id = info_data[0]["id"]
            print("\n6. Testing /movie/detail-info for id:", movie_id)
            r7 = requests.post(f"{base_url}/movie/detail-info", data={"id": str(movie_id), "token": token}, headers=headers, verify=False)
            j7 = r7.json()
            print("Detail title:", j7.get("info", {}).get("title"))
            print("Detail links count:", len(j7.get("link", [])))
    except Exception as e:
        print("Search JSON error:", e)

if __name__ == "__main__":
    test_api()
