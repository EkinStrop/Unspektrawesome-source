"""Shared OFX stock ordering for the self-contained native plugin build."""

from __future__ import annotations


DEFAULT_FILM_STOCK = "kodak_portra_400"
DEFAULT_PAPER_STOCK = "kodak_portra_endura"

_LEGACY_FILM_ORDER = (
    "kodak_ektar_100",
    "kodak_portra_160",
    "kodak_portra_400",
    "kodak_portra_800",
    "kodak_portra_800_push1",
    "kodak_portra_800_push2",
    "kodak_gold_200",
    "kodak_ultramax_400",
    "kodak_vision3_50d",
    "kodak_vision3_250d",
    "kodak_verita_200d",
    "kodak_vision3_200t",
    "kodak_vision3_500t",
    "fujifilm_pro_400h",
    "fujifilm_c200",
    "fujifilm_xtra_400",
    "kodak_ektachrome_100",
    "kodak_kodachrome_64",
    "fujifilm_velvia_100",
    "fujifilm_provia_100f",
)

_LEGACY_PAPER_ORDER = (
    "kodak_endura_premier",
    "kodak_ultra_endura",
    "kodak_ektacolor_edge",
    "kodak_supra_endura",
    "kodak_portra_endura",
    "fujifilm_crystal_archive_typeii",
    "kodak_2383",
    "kodak_2393",
)


FILMS = list(_LEGACY_FILM_ORDER)
PAPERS = list(_LEGACY_PAPER_ORDER)
DEFAULT_FILM_INDEX = FILMS.index(DEFAULT_FILM_STOCK)
DEFAULT_PAPER_INDEX = PAPERS.index(DEFAULT_PAPER_STOCK)


__all__ = [
    "DEFAULT_FILM_INDEX",
    "DEFAULT_FILM_STOCK",
    "DEFAULT_PAPER_INDEX",
    "DEFAULT_PAPER_STOCK",
    "FILMS",
    "PAPERS",
]
