"""Read-only client for the HihFilm API."""

from __future__ import annotations

import http.cookiejar
import json
from pathlib import Path
from typing import Any, Optional, TypeAlias
from urllib.parse import urljoin, urlparse

import requests


BootData: TypeAlias = dict[str, Any]
Movie: TypeAlias = dict[str, Any]
Series: TypeAlias = dict[str, Any]
Channel: TypeAlias = dict[str, Any]
Season: TypeAlias = dict[str, Any]
Episode: TypeAlias = dict[str, Any]
Source: TypeAlias = dict[str, Any]


class NullCookiePolicy(http.cookiejar.DefaultCookiePolicy):
    """Reject every cookie stored or returned by the shared session."""

    def set_ok(self, cookie, request):
        return False

    def return_ok(self, cookie, request):
        return False

    def domain_return_ok(self, domain, request):
        return False

    def path_return_ok(self, path, request):
        return False


class HihfilmClient:
    def __init__(self, config: Optional[dict[str, Any]] = None):
        if config is None:
            config_path = Path(__file__).with_name("config.json")
            config = json.loads(config_path.read_text(encoding="utf-8"))
        self.config = config
        self.base = str(config["base"]).rstrip("/") + "/"
        if urlparse(self.base).scheme != "https":
            raise ValueError("HihFilm requires an HTTPS base URL")
        self.session = requests.Session()
        self.session.cookies = requests.cookies.RequestsCookieJar()
        self.session.cookies.set_policy(NullCookiePolicy())
        self.session.headers.clear()
        self.session.headers.update(config.get("headers", {}))

    def _url(self, path_name: str, **values: Any) -> str:
        values.setdefault("KEY", self.config["static_key"])
        path = self.config["paths"][path_name].format(**values)
        url = urljoin(self.base, path)
        if urlparse(url).scheme != "https":
            raise ValueError("HihFilm API requests must use HTTPS")
        return url

    @staticmethod
    def _parse_json(response: requests.Response) -> Any:
        if not response.ok:
            return []
        try:
            return response.json()
        except (ValueError, TypeError):
            try:
                return json.loads(response.text)
            except (ValueError, TypeError, AttributeError):
                return []

    @staticmethod
    def _unwrap(payload: Any) -> Any:
        if not isinstance(payload, dict) or "code" not in payload:
            return payload
        if str(payload.get("code")) not in {"200", "0", "1"}:
            return []
        for key in ("values", "data", "result"):
            if key in payload:
                return payload[key]
        return payload

    @staticmethod
    def _list(payload: Any, *keys: str) -> list[dict[str, Any]]:
        if isinstance(payload, list):
            return [item for item in payload if isinstance(item, dict)]
        if isinstance(payload, dict):
            for key in keys:
                value = payload.get(key)
                if isinstance(value, list):
                    return [item for item in value if isinstance(item, dict)]
        return []

    def _get_json(self, path_name: str, **values: Any) -> Any:
        try:
            response = self.session.get(self._url(path_name, **values), timeout=15)
        except requests.RequestException:
            return []
        return self._unwrap(self._parse_json(response))

    def register_device(self, device_id: str) -> dict[str, Any]:
        try:
            response = self.session.post(
                self._url("device", DEVICE=device_id),
                json={"device_id": device_id, "install_key": self.config.get("install_key", 0)},
                headers=self.config.get("post_headers", {}),
                timeout=15,
            )
        except requests.RequestException:
            return {}
        payload = self._parse_json(response)
        return payload if isinstance(payload, dict) else {}

    def boot(self) -> BootData:
        """Run the captured startup sequence in its documented order."""
        registration = self.register_device(str(self.config["device_id"]))
        return {
            "device": registration,
            "version": self._get_json("version", ver=62, uid=0),
            "home": self._get_json("home"),
            "genres": self._get_json("genres"),
            "countries": self._get_json("countries"),
            "categories": self._get_json("categories"),
            "packages": self._get_json("packages"),
            "movies": self.list_movies(),
            "series": self.list_series(),
            "channels": self.list_channels(),
        }

    def list_movies(self, g: int = 0, sort: str = "created", c: int = 0) -> list[Movie]:
        return self._list(self._get_json("movies", g=g, sort=sort, c=c), "movies", "data", "values")

    def list_series(self, g: int = 0, sort: str = "created", c: int = 0) -> list[Series]:
        return self._list(self._get_json("series", g=g, sort=sort, c=c), "series", "data", "values")

    def list_channels(self, g: int = 0, sort: int = 0, c: int = 0) -> list[Channel]:
        return self._list(self._get_json("channels", g=g, sort=sort, c=c), "channels", "data", "values")

    def seasons(self, series_id: int) -> list[Season]:
        return self._list(self._get_json("seasons", sid=series_id), "seasons", "data", "values")

    def episodes(self, series_id: int) -> list[Episode]:
        found: list[Episode] = []
        for season in self.seasons(series_id):
            episodes = season.get("episodes", [])
            if isinstance(episodes, list):
                found.extend(item for item in episodes if isinstance(item, dict))
        return found

    def sources(self, series_id: int, episode_id: int) -> list[Source]:
        episode = next(
            (item for item in self.episodes(series_id) if str(item.get("id")) == str(episode_id)),
            None,
        )
        if not episode or not isinstance(episode.get("sources"), list):
            return []
        sources: list[Source] = []
        for source in episode["sources"]:
            if not isinstance(source, dict):
                continue
            normalized = dict(source)
            if normalized.get("url"):
                normalized["url"] = urljoin(self.base, str(normalized["url"]))
            sources.append(normalized)
        return sources

    def media_url(self, series_id: int, episode_id: int, quality: int) -> str:
        for source in self.sources(series_id, episode_id):
            if str(source.get("quality")) == str(quality):
                return str(source.get("url", ""))
        return ""

    def fetch(self, url: str, byte_range: Optional[str] = None) -> bytes:
        if urlparse(url).scheme != "https":
            return b""
        headers = {"Range": byte_range} if byte_range else None
        try:
            response = self.session.get(url, headers=headers, timeout=30)
        except requests.RequestException:
            return b""
        return response.content if response.ok else b""
