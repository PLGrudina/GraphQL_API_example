package controllers

import com.google.inject.{Inject, Singleton}
import graphql.{GraphQL, GraphQLContext, GraphQLContextFactory}
import play.api.Configuration
import play.api.libs.json._
import play.api.mvc._
import sangria.ast.Document
import sangria.execution._
import sangria.marshalling.playJson._
import sangria.parser.QueryParser

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


/**
  * This controller creates an `Action` to handle HTTP requests to the
  * application's home page.
  */
@Singleton
class AppController @Inject()(cc: ControllerComponents,
                              graphQLContextFactory: GraphQLContextFactory,
                              env: play.Environment,
                              config: Configuration,
                              implicit val executionContext: ExecutionContext) extends AbstractController(cc) {

  /**
    * An action that renders an page with an in-browser IDE for exploring GraphQL.
    * The configuration in the 'routes' file means that
    * this method will be called when the application receives a
    * 'GET' request with a path of '/'.
    */
  def graphiql = Action(Ok(views.html.graphiql()))

  /**
    * Parse graphql body of incoming request.
    */
  def graphqlBody: Action[JsValue] = Action.async(parse.json) {
    implicit request: Request[JsValue] =>

      val extract: JsValue => (String, Option[String], Option[JsObject]) = query => (
        (query \ "query").as[String],
        (query \ "operationName").asOpt[String],
        (query \ "variables").toOption.flatMap {
          case JsString(vars) => Some(parseVariables(vars))
          case obj: JsObject => Some(obj)
          case _ ⇒ None
        }
      )

      val maybeQuery: Try[(String, Option[String], Option[JsObject])] = Try {
        request.body match {
          case arrayBody@JsArray(_) => extract(arrayBody.value(0))
          case objectBody@JsObject(_) => extract(objectBody)
          case otherType =>
            throw new Error {
              s"/graphql endpoint does not support request body of type [${otherType.getClass.getSimpleName}]"
            }
        }
      }

      maybeQuery match {
        case Success((query, operationName, variables)) => executeQuery(query, variables, operationName) {
          graphQLContextFactory.createContextForRequest()
        }
        case Failure(error) => Future.successful {
          BadRequest(error.getMessage)
        }
      }
  }

  /**
    * The function analyzes and executes incoming graphql query, and returns execution result.
    */
  def executeQuery(query: String, variables: Option[JsObject] = None, operation: Option[String] = None)
                  (graphQLContext: GraphQLContext): Future[Result] = QueryParser.parse(query) match {
    case Success(queryAst: Document) => Executor.execute(
      schema = GraphQL.Schema,
      queryAst = queryAst,
      userContext = graphQLContext,
      variables = variables.getOrElse(Json.obj()),
      exceptionHandler = exceptionHandler,
      queryReducers = List(
        QueryReducer.rejectMaxDepth[GraphQLContext](GraphQL.maxQueryDepth),
        QueryReducer.rejectComplexQueries[GraphQLContext](GraphQL.maxQueryComplexity, (_, _) => TooComplexQueryError)
      )
    ).map(Ok(_)).flatMap {
      result =>
        Future(result)
    }.recover {
      case error: QueryAnalysisError ⇒ BadRequest(error.resolveError)
      case error: ErrorWithResolver ⇒ InternalServerError(error.resolveError)
    }
    case Failure(ex) => Future.successful(Ok(s"${ex.getMessage}"))
  }

  /**
    * Helper function for parsing variables of incoming query.
    *
    * @param variables variables from incoming query
    * @return JsObject with variables
    */
  def parseVariables(variables: String): JsObject = if (variables.trim.isEmpty || variables.trim == "null") Json.obj()
  else Json.parse(variables).as[JsObject]

  lazy val exceptionHandler = ExceptionHandler {
    case (_, error@TooComplexQueryError) ⇒ HandledException(error.getMessage)
    case (_, error@MaxQueryDepthReachedError(_)) ⇒ HandledException(error.getMessage)
  }

  case object TooComplexQueryError extends Exception("Query is too expensive.")

}
