from app.main import app, package_version


def test_openapi_version_matches_installed_package() -> None:
    assert app.version != "0.1.0"
    assert app.version == package_version()
