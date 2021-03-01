## Paperhands app backend codebase

### Usage

* run migrations `make flyway-migrate`
* run server `make run-server`
* run scraper `make run-scrape`
* dev auto restart:
** `make sbt`
** `~reStart server`
** or `~reStart scrape`

### Dependencies

You need to have `tesseract` OCR CLI installed and available on your `$PATH`
