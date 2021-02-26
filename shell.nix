let
   pkgs = import <nixpkgs> {};
in pkgs.stdenv.mkDerivation rec {
  name = "pepehands";
  buildInputs = with pkgs; [
    stdenv
    glib
    pkgconfig
    leptonica
    tesseract_4
  ];
   LD_LIBRARY_PATH = "${pkgs.stdenv.lib.makeLibraryPath buildInputs}";
}
