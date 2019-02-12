package specs

import play.api.libs.json.{JsValue, Json}

/**
  * Contains variables with prepared query/mutations in JSON format.
  */
trait PreparedInput {
  lazy val addPost: JsValue = Json.parse("""{ "query": "mutation { addPost(title:\"New post\" content:\"I created a post\") { id title content } }" }""")
  lazy val updatePost: JsValue = Json.parse("""{ "query": "mutation { updatePost( id: 2 title: \"New title\" content: \"X#X#X#X#X#\"){ id title content } }" }""")
  lazy val deletePost: JsValue = Json.parse("""{ "query": "mutation { deletePost( id: 1 ) }" }""")

  lazy val tryUpdateNonExistingPost: JsValue = Json.parse("""{ "query": "mutation { updatePost( id: 20 title: \"New title\" content: \"X#X#X#X#X#\"){ id title content } }" }""")
  lazy val tryDeleteNonExistingPost: JsValue = Json.parse("""{ "query": "mutation { deletePost( id: 25 ) }" }""")

  lazy val findPost: JsValue = Json.parse("""{ "query": "query { findPost(id:1) { id title content} }" }""")
  lazy val posts: JsValue = Json.parse("""{ "query": "query { posts { id title content } }"}""")
}
