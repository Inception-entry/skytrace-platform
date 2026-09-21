from importlib.metadata import version


def test_h2_lock_is_at_least_advisory_fix() -> None:
    major, minor, patch = (int(part) for part in version("h2").split(".")[:3])
    assert (major, minor, patch) >= (4, 4, 1)
