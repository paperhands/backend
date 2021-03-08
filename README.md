## Paperhands app backend codebase

Watch [WSB tutorial](https://www.youtube.com/watch?v=c1s3Iekns9k)

### Requirements
* sbt
* make
* tesseract
* docker

### Usage

* bring compose up `make compose-reset` - this should fix permissions in docker folder
* run migrations `make flyway-migrate`
* run scraper `make run-scrape`
* run http server `make run-server`
* dev auto restart:
  * `make sbt`
  * `~reStart server`
  * or `~reStart scrape`

### Dependencies

You need to have `tesseract` OCR CLI installed and available on your `$PATH`
