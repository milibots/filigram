from urllib.parse import urlparse

import pytest

from engines.simba.client import SimbaClient


KEY = "test-key"
CONFIG = {
    "base": "http://example.test",
    "static_key": KEY,
    "headers": {"User-Agent": "test"},
    "paths": {
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

    def json(self):
        return self.payload


@pytest.fixture
def client(monkeypatch):
    instance = SimbaClient(CONFIG)

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
                    "sources": [{"quality": 720, "url": "/media/episode-10.mp4"}],
                }],
            }])
        if "/serie/" in path:
            return FakeResponse({"code": 200, "values": [{"id": 35344, "type": "serie", "title": "Series"}]})
        if "/channel/" in path:
            return FakeResponse([{"id": 2, "title": "Channel", "url": "http://media.test/live.m3u8"}])
        if url.startswith("http://example.test/media/"):
            assert kwargs.get("headers") == {"Range": "bytes=0-0"}
            return FakeResponse(content=b"x", status=206)
        return FakeResponse([])

    monkeypatch.setattr(instance.session, "get", fake_get)
    return instance


def test_boot_returns_all_lists(client):
    boot = client.boot()
    assert set(boot) == {"version", "home", "genres", "countries", "categories", "packages", "movies", "series", "channels"}
    assert all(isinstance(boot[key], list) for key in boot)


def test_series_list_nonempty(client):
    assert client.list_series()


def test_seasons_returns_episodes(client):
    assert client.seasons(35344)[0]["episodes"]


def test_episode_has_sources(client):
    assert client.episodes(35344)[0]["sources"]


def test_source_urls_are_absolute(client):
    assert client.sources(35344, 10)[0]["url"].startswith("http://example.test/")


def test_media_url_head_returns_2xx_or_206(client):
    url = client.media_url(35344, 10, 720)
    assert url
    assert client.fetch(url, byte_range="bytes=0-0") == b"x"
