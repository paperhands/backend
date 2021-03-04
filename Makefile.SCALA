flyway-migrate:
	sbt "run flyway migrate"
flyway-clean:
	sbt "run flyway clean"
flyway-info:
	sbt "run flyway info"
run-scrape:
	sbt "run scrape"
run-server:
	sbt "run server"
run-export:
	sbt "run export"
test:
	sbt test
uberjar:
	sbt clean assembly
sbt:
	sbt
