package app.paperhands.scraper

import app.paperhands.reddit.{Reddit, RedditComment, RedditPost}
import app.paperhands.config.Config

object RedditScraper extends Reddit {
  def handleComment(comment: RedditComment) = {
    println(comment.name)
  }
  def handlePost(post: RedditPost) = {
    println(post.name)
  }
}

object Scraper {
  def run = {
    val cfg = Config.load
    RedditScraper.loop(cfg.reddit.secret)
  }
}
