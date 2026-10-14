from urllib.parse import urlparse

import pytest

from engines.hihfilm.client import HihfilmClient


KEY = "test-key"
CONFIG = {
    "base": "https://example.test",
    "static_key": KEY,
    "device_id": "device-1",
    "install_key": 0,
    "headers": {"User-Agent": "test"},
    "post_headers": {"Content-Type": "application/json; charset=UTF-8"},
    "paths": {
        "device": "/api/device/{DEVICE}/{KEY}/",
        "version": "/api/version/check/{ver}/{uid}/{KEY}/",
        "home": "/api/first/{KEY}/",
        "genres": "/api/genre/all/{KEY}/",
        "countries": "/api/country/all/{KEY}/",
        "categories": "/api/category/all/{KEY}/",
        "packages": "/api/package/all/{KEY}/",
        "movies": "/api/movie/by/filtres/{g}/{sort}/{c}/{KEY}/",
        "series": "/api/serie/by/filtres/{g}/{sort}/{c}/{KEY}/",
        "channels": "/api/channel/by/filtres/{g}/{sort}/{c}/{KEY}/",
        "seasons": "/api/season/by/serie/{sid}/{KEY}/",
    },
}


class FakeResponse:
    def __init__(self, payload=None, content=b"media", status=200):
        self.payload = payload
        self.content = content
        self.status_code = status
        self.ok = 200 <= status < 300
        self.text = ""

    def json(self):
        return self.payload


@pytest.fixture
def client(monkeypatch):
    instance = HihfilmClient(CONFIG)

    def fake_post(url, **kwargs):
        assert "/api/device/device-1/" in url
        assert kwargs["json"]["install_key"] == 0
        return FakeResponse({"code": "200", "message": "updated", "values": []})

    def fake_get(url, **kwargs):
        path = urlparse(url).path
        if "/movie/" in path:
            return FakeResponse([{"id": 1, "type": "movie", "title": "Movie"}])
        if "/season/" in path:
            return FakeResponse([{
                "id": 9,
                "title": "Season 1",
                "episodes": [{
                    "id": 10,
                    "title": "Episode 1",
                    "sources": [{"quality": 720, "url": "https://media.test/episode-10.mp4"}],
                }],
            }])
        if "/serie/" in path:
            return FakeResponse({"code": 200, "values": [{"id": 7, "type": "serie", "title": "Series"}]})
        if "/channel/" in path:
            return FakeResponse([{"id": 2, "title": "Channel", "url": "https://media.test/live.m3u8"}])
        if url.startswith("https://media.test/"):
            assert kwargs.get("headers") == {"Range": "bytes=0-0"}
            return FakeResponse(content=b"x", status=206)
        return FakeResponse([])

    monkeypatch.setattr(instance.session, "post", fake_post)
    monkeypatch.setattr(instance.session, "get", fake_get)
    return instance


def test_device_register_returns_wrapper(client):
    result = client.register_device("device-1")
    assert str(result["code"]) == "200"
    assert isinstance(result["values"], list)


def test_boot_returns_all_lists(client):
    boot = client.boot()
    assert set(boot) == {
        "device", "version", "home", "genres", "countries", "categories",
        "packages", "movies", "series", "channels",
    }
    assert all(isinstance(boot[key], list) for key in (
        "version", "home", "genres", "countries", "categories", "packages",
        "movies", "series", "channels",
    ))


def test_series_list_nonempty(client):
    assert client.list_series()


def test_seasons_returns_episodes(client):
    assert client.seasons(7)[0]["episodes"]


def test_episode_has_sources(client):
    assert client.episodes(7)[0]["sources"]


def test_source_urls_are_absolute(client):
    assert client.sources(7, 10)[0]["url"].startswith("https://")


def test_media_url_head_returns_2xx_or_206(client):
    url = client.media_url(7, 10, 720)
    assert url
    assert client.fetch(url, byte_range="bytes=0-0") == b"x"
