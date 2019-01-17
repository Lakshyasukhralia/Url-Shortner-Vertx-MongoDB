package io.vertx.blog.first;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.util.List;
import java.util.stream.Collectors;


public class MyFirstVerticle extends AbstractVerticle {

  public static final String COLLECTION = "whiskies";
  private MongoClient mongo;

  @Override
  public void start(Future<Void> fut) {

    // Create a Mongo client
    mongo = MongoClient.createShared(vertx, config());


    createSomeData(
        (nothing) -> startWebApp(
            (http) -> completeStartup(http, fut)
        ), fut);
  }

  private void startWebApp(Handler<AsyncResult<HttpServer>> next) {

    // Create a router object.
    Router router = Router.router(vertx);
    // Bind "/" to our hello message.
    router.route("/").handler(routingContext -> {
      HttpServerResponse response = routingContext.response();
      response
          .putHeader("content-type", "text/html")
          .end("<h1>Server is up and running!</h1>");
    });

    router.route("/assets/*").handler(StaticHandler.create("assets"));
    router.get("/api/whiskies").handler(this::getAll);
    router.route("/api/whiskies*").handler(BodyHandler.create());
    router.route("/:origin").handler(this::getOne);
    router.post("/api/whiskies").handler(this::addOne);
    //router.get("/api/whiskies/:id").handler(this::getOne);
    router.put("/api/whiskies/:id").handler(this::updateOne);
    router.delete("/api/whiskies/:id").handler(this::deleteOne);


    // Create the HTTP server and pass the "accept" method to the request handler.
    vertx
        .createHttpServer()
        .requestHandler(router::accept)
        .listen(
            // Retrieve the port from the configuration,
            // default to 8080.
            config().getInteger("http.port", 8080),
            next
        );
  }

  private void completeStartup(AsyncResult<HttpServer> http, Future<Void> fut) {
    if (http.succeeded()) {
      fut.complete();
    } else {
      fut.fail(http.cause());
    }
  }


  @Override
  public void stop() {
    mongo.close();
  }

  private void addOne(RoutingContext routingContext) {
    final Whisky whisky = Json.decodeValue(routingContext.getBodyAsString(),
        Whisky.class);

    mongo.insert(COLLECTION, whisky.toJson(), r ->
        routingContext.response()
            .setStatusCode(201)
            .putHeader("content-type", "application/json; charset=utf-8")
            .end(Json.encodePrettily(whisky.setId(r.result()))));
  }




  private void getOne(RoutingContext routingContext) {
    final String id = routingContext.request().getParam("origin");
    //String k = routingContext.request().getParam("origin");

    //JsonObject json = routingContext.getBodyAsJson();
    if (id == null) {
      routingContext.response().setStatusCode(400).end();
    } else {
      mongo.findOne(COLLECTION, new JsonObject().put("origin", id), null, ar -> {
        if (ar.succeeded()) {
          if (ar.result() == null) {
            routingContext.response().setStatusCode(404).end();
            return;
          }
          //console.log("");
          String url = ar.result().getString("name");
          writeFile(url,id);
          //String url = "www.facebook.com";

          Whisky whisky = new Whisky(ar.result());
          routingContext.response()
                  .setStatusCode(302)
                  .putHeader("Location", url).end();

          mongo.updateCollection(COLLECTION, new JsonObject().put("origin", id),new JsonObject().put("$inc",new JsonObject().put("hits", 1)), res -> {
            if(res.succeeded()) {
              System.out.println("Name is " + res.result());
            }else{
              res.cause().printStackTrace();
            }
          });

        } else {
          routingContext.response().setStatusCode(404).end();
        }
      });

    }

  }



  private void updateOne(RoutingContext routingContext) {
    final String id = routingContext.request().getParam("id");
    JsonObject json = routingContext.getBodyAsJson();
    if (id == null || json == null) {
      routingContext.response().setStatusCode(400).end();
    } else {
      mongo.update(COLLECTION,
          new JsonObject().put("_id", id), // Select a unique document
          // The update syntax: {$set, the json object containing the fields to update}
          new JsonObject()
              .put("$set", json),
          v -> {
            if (v.failed()) {
              routingContext.response().setStatusCode(404).end();
            } else {
              routingContext.response()
                  .putHeader("content-type", "application/json; charset=utf-8")
                  .end(Json.encodePrettily(new Whisky(id, json.getString("name"), json.getString("origin"))));
            }
          });
    }
  }


  private Future<String> writeFile(String input,String wrt) {
    Future<String> future = Future.future();
    String file = wrt+".txt";
    // Retrieve a FileSystem object from vertx instance and call the
    // non-blocking writeFile method
    vertx.fileSystem().writeFile(file, Buffer.buffer(input), handler -> {
      if (handler.succeeded()) {
        System.out.println("\n File written with " + input);
        future.complete(file);
      } else {
        System.err.println("Error while writing in file: " + handler.cause().getMessage());
      }
    });

    return future;
  }

  private void deleteOne(RoutingContext routingContext) {
    String id = routingContext.request().getParam("id");
    if (id == null) {
      routingContext.response().setStatusCode(400).end();
    } else {
      mongo.removeOne(COLLECTION, new JsonObject().put("_id", id),
          ar -> routingContext.response().setStatusCode(204).end());
    }
  }

  private void getAll(RoutingContext routingContext) {
    mongo.find(COLLECTION, new JsonObject(), results -> {
      List<JsonObject> objects = results.result();
      List<Whisky> whiskies = objects.stream().map(Whisky::new).collect(Collectors.toList());
      routingContext.response()
          .putHeader("content-type", "application/json; charset=utf-8")
          .end(Json.encodePrettily(whiskies));
    });
  }

  private void createSomeData(Handler<AsyncResult<Void>> next, Future<Void> fut) {
    Whisky bowmore = new Whisky("https://stackoverflow.com/questions/46423356/vertx-httpcllentrequest-redirection","WXjhi");
    Whisky talisker = new Whisky("https://www.reddit.com/r/node/comments/80xgah/how_to_build_a_url_shortener_with_nodejs_and/", "RwTkl");
    System.out.println(bowmore.toJson());

    // Do we have data in the collection ?
    mongo.count(COLLECTION, new JsonObject(), count -> {
      if (count.succeeded()) {
        if (count.result() == 0) {
          // no whiskies, insert data
          mongo.insert(COLLECTION, bowmore.toJson(), ar -> {
            if (ar.failed()) {
              fut.fail(ar.cause());
            } else {
              mongo.insert(COLLECTION, talisker.toJson(), ar2 -> {
                if (ar2.failed()) {
                  fut.fail(ar2.cause());
                } else {
                  next.handle(Future.succeededFuture());
                }
              });
            }
          });
        } else {
          next.handle(Future.succeededFuture());
        }
      } else {
        // report the error
        fut.fail(count.cause());
      }
    });
  }
}
