/*
 * Knot.x - Reactive microservice assembler - HTTP Server
 *
 * Copyright (C) 2016 Cognifide Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cognifide.knotx.server;

import com.cognifide.knotx.dataobjects.RepositoryRequest;
import com.cognifide.knotx.dataobjects.RepositoryResponse;
import com.cognifide.knotx.dataobjects.TemplateEngineRequest;
import com.cognifide.knotx.dataobjects.TemplateEngineResponse;

import java.io.IOException;
import java.net.URISyntaxException;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.eventbus.EventBus;
import io.vertx.rxjava.core.eventbus.Message;
import io.vertx.rxjava.core.http.HttpServer;
import io.vertx.rxjava.core.http.HttpServerRequest;
import io.vertx.rxjava.core.http.HttpServerResponse;

public class KnotxServerVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LoggerFactory.getLogger(KnotxServerVerticle.class);

  private KnotxServerConfiguration configuration;

  private HttpServer httpServer;

  private String repositoryAddress;

  private String engineAddress;

  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);

    this.repositoryAddress = config().getJsonObject("dependencies").getString("repository.address");
    this.engineAddress = config().getJsonObject("dependencies").getString("engine.address");
    configuration = new KnotxServerConfiguration(config());
  }

  @Override
  public void start(Future<Void> fut) throws IOException, URISyntaxException {
    LOGGER.debug("Starting <{}>", this.getClass().getName());
    httpServer = vertx.createHttpServer();
    EventBus eventBus = vertx.eventBus();

    httpServer.requestHandler(
        request -> {
          request.setExpectMultipart(true);
          request.endHandler(aVoid -> eventBus.<JsonObject>sendObservable(repositoryAddress, createRepositoryRequest(request))
              .doOnNext(this::traceMessage)
              .subscribe(
                  reply -> {
                    RepositoryResponse repository = RepositoryResponse.fromJson(reply.body());
                    if (repository.shouldProcess()) {
                      eventBus.<JsonObject>sendObservable(engineAddress, createEngineRequest(repository, request))
                          .subscribe(
                              result -> {
                                TemplateEngineResponse engineResponse = new TemplateEngineResponse(result.body());
                                HttpServerResponse response = request.response();
                                if (engineResponse.isSuccess()) {
                                  response.headers().addAll(repository.getHeaders());
                                  String responseHtml = engineResponse.getHtml();
                                  response.putHeader("Content-Length", String.valueOf(responseHtml.length())).end(responseHtml);
                                } else {
                                  response.setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end(engineResponse.getReason());
                                }
                              },
                              error -> {
                                LOGGER.error("Error happened", error);
                                request.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end(error.toString());
                              }
                          );
                    } else {
                      request.response().headers().addAll(repository.getHeaders());
                      request.response().setStatusCode(repository.getStatusCode()).end();
                    }
                  },
                  error -> LOGGER.error("Error: ", error)
              ));
        }
    ).listen(
        configuration.httpPort(),
        result -> {
          if (result.succeeded()) {
            LOGGER.info("Knot.x HTTP Server started. Listening on port {}", configuration.httpPort());
            fut.complete();
          } else {
            LOGGER.error("Unable to start Knot.x HTTP Server.", result.cause());
            fut.fail(result.cause());
          }
        });
  }

  @Override
  public void stop() throws Exception {
    httpServer.close();
  }

  private JsonObject createRepositoryRequest(HttpServerRequest request) {
    return new RepositoryRequest(request.path(), request.headers()).toJsonObject();
  }

  private JsonObject createEngineRequest(RepositoryResponse repositoryResponse, HttpServerRequest request) {
    return new TemplateEngineRequest(
        repositoryResponse.getData(),
        request.method(),
        request.headers(),
        request.params(),
        request.formAttributes(),
        request.uri())
        .toJsonObject();
  }

  private void traceMessage(Message<JsonObject> message) {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Got message from <template-repository> with value <{}>", message.body().encodePrettily());
    }
  }
}