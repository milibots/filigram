"""Read-only client for the Simba / Fitlinerim API."""

from __future__ import annotations

import http.cookiejar
from pathlib import Path
import json
from typing import Any, Optional
from urllib.parse import urljoin

import requests


class NullCookiePolicy(http.cookiejar.DefaultCookiePolicy):
    """Reject stored and returned cookies for this unauthenticated engine."""

    def set_ok(self, cookie, request):
        return False

    def return_ok(self, cookie, request):
        return False

    def domain_return_ok(self, domain, request):
        return False

    def path_return_ok(self, path, request):
        return False


class SimbaClient:
    def __init__(self, config: Optional[dict[str, Any]] = None):
        if config is None:
            config_path = Path(__file__).with_name("config.json")
            config = json.loads(config_path.read_text(encoding="utf-8"))
        self.config = config
        self.base = str(config["base"]).rstrip("/") + "/"
        self.session = requests.Session()
        self.session.cookies = requests.cookies.RequestsCookieJar()
        self.session.cookies.set_policy(NullCookiePolicy())
        self.session.headers.clear()
        self.session.headers.update(config.get("headers", {}))

    def _url(self, path_name: str, **values: Any) -> str:
        values.setdefault("KEY", self.config["static_key"])
        path = self.config["paths"][path_name].format(**values)
        return urljoin(self.base, path)

    def _json(self, path_name: str, **values: Any) -> Any:
        try:
            response = self.session.get(self._url(path_name, **values), timeout=15)
        except requests.RequestException:
            return []
        if not response.ok:
            return []
        try:
            payload = response.json()
        except (ValueError, TypeError):
            return []
        return self._unwrap(payload)

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

    def boot(self) -> dict[str, Any]:
        """Run the documented startup sequence and return all discovered lists."""
        result = {
            "version": self._json("version", ver=1, uid=0),
            "home": self._json("home"),
            "genres": self._json("genres"),
            "countries": self._json("countries"),
            "categories": self._json("categories"),
            "packages": self._json("packages"),
            "movies": self.list_movies(),
            "series": self.list_series(),
            "channels": self.list_channels(),
        }
        return result

    def list_movies(self, g: int = 0, sort: str = "created", c: int = 0) -> list[dict[str, Any]]:
        return self._list(self._json("movies", g=g, sort=sort, c=c), "movies", "data", "values")

    def list_series(self, g: int = 0, sort: str = "created", c: int = 0) -> list[dict[str, Any]]:
        return self._list(self._json("series", g=g, sort=sort, c=c), "series", "data", "values")

    def list_channels(self, g: int = 0, sort: int = 0, c: int = 0) -> list[dict[str, Any]]:
        return self._list(self._json("channels", g=g, sort=sort, c=c), "channels", "data", "values")

    def seasons(self, series_id: int) -> list[dict[str, Any]]:
        return self._list(self._json("seasons", sid=series_id), "seasons", "data", "values")

    def episodes(self, series_id: int) -> list[dict[str, Any]]:
        found: list[dict[str, Any]] = []
        for season in self.seasons(series_id):
            episodes = season.get("episodes", [])
            if isinstance(episodes, list):
                found.extend(item for item in episodes if isinstance(item, dict))
        return found

    def sources(self, series_id: int, episode_id: int) -> list[dict[str, Any]]:
        episode = next(
            (item for item in self.episodes(series_id) if str(item.get("id")) == str(episode_id)),
            None,
        )
        if not episode or not isinstance(episode.get("sources"), list):
            return []
        sources = []
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
        headers = {"Range": byte_range} if byte_range else None
        try:
            response = self.session.get(url, headers=headers, timeout=30)
        except requests.RequestException:
            return b""
        return response.content if response.ok else b""
