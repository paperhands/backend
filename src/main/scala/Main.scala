import app.paperhands.reddit.{Reddit, RedditComment, RedditPost}

object RedditScraper extends Reddit {
  def handleComment(comment: RedditComment) = {
    println(comment.name)
  }
  def handlePost(post: RedditPost) = {
    println(post.name)
  }
}

// @main def hello: Unit =
object Main extends App {
  RedditScraper.loop()
}
