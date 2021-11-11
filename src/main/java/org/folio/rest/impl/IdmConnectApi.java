package org.folio.rest.impl;

import static org.folio.rest.impl.Constants.MSG_IDM_URL_NOT_SET;
import static org.folio.rest.impl.Constants.TABLE_NAME_CONTRACTS;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Stream;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import org.folio.rest.jaxrs.model.Contract;
import org.folio.rest.jaxrs.model.Contracts;
import org.folio.rest.jaxrs.resource.IdmConnect;
import org.folio.rest.persist.PgUtil;

public class IdmConnectApi implements IdmConnect {

  @Override
  public void getIdmConnectContract(
      String query,
      int offset,
      int limit,
      String lang,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    PgUtil.get(
        TABLE_NAME_CONTRACTS,
        Contract.class,
        Contracts.class,
        query,
        offset,
        limit,
        okapiHeaders,
        vertxContext,
        GetIdmConnectContractResponse.class,
        asyncResultHandler);
  }

  @Override
  public void postIdmConnectContract(
      String lang,
      Contract entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    PgUtil.post(
        TABLE_NAME_CONTRACTS,
        entity,
        okapiHeaders,
        vertxContext,
        PostIdmConnectContractResponse.class,
        asyncResultHandler);
  }

  @Override
  public void getIdmConnectContractById(
      String id,
      String lang,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    PgUtil.getById(
        TABLE_NAME_CONTRACTS,
        Contract.class,
        id,
        okapiHeaders,
        vertxContext,
        GetIdmConnectContractByIdResponse.class,
        asyncResultHandler);
  }

  @Override
  public void deleteIdmConnectContractById(
      String id,
      String lang,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    PgUtil.deleteById(
        TABLE_NAME_CONTRACTS,
        id,
        okapiHeaders,
        vertxContext,
        DeleteIdmConnectContractByIdResponse.class,
        asyncResultHandler);
  }

  @Override
  public void putIdmConnectContractById(
      String id,
      String lang,
      Contract entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    PgUtil.put(
        TABLE_NAME_CONTRACTS,
        entity,
        id,
        okapiHeaders,
        vertxContext,
        PutIdmConnectContractByIdResponse.class,
        asyncResultHandler);
  }

  private String toBasicIsoDate(String dateString) {
    try {
      return LocalDate.parse(dateString).format(DateTimeFormatter.BASIC_ISO_DATE);
    } catch (NullPointerException | DateTimeException e) {
      return dateString;
    }
  }

  private Response toResponse(HttpResponse<Buffer> bufferHttpResponse) {
    ResponseBuilder responseBuilder =
        Response.status(bufferHttpResponse.statusCode())
            .header("Content-Type", bufferHttpResponse.getHeader("Content-Type"))
            .entity(bufferHttpResponse.bodyAsString());
    return responseBuilder.build();
  }

  private HttpRequest<Buffer> createIdmRequest(
      WebClient webClient,
      String idmUrl,
      String idmToken,
      String firstname,
      String lastname,
      String dateOfBirth) {
    HttpRequest<Buffer> bufferHttpRequest = webClient.getAbs(idmUrl);
    if (idmToken != null) {
      bufferHttpRequest.putHeader("Authorization", idmToken);
    }

    Stream.of(
            new String[] {"givenname", firstname},
            new String[] {"surname", lastname},
            new String[] {"date_of_birth", toBasicIsoDate(dateOfBirth)})
        .forEach(
            a -> {
              if (a[1] != null) {
                bufferHttpRequest.addQueryParam(a[0], a[1]);
              }
            });
    return bufferHttpRequest;
  }

  @Override
  public void getIdmConnectSearchidm(
      String firstname,
      String lastname,
      String dateOfBirth,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    String idmUrl = System.getenv("IDM_URL");
    String idmToken = System.getenv("IDM_TOKEN");

    if (idmUrl == null) {
      asyncResultHandler.handle(
          Future.succeededFuture(
              GetIdmConnectSearchidmResponse.respond500WithTextPlain(MSG_IDM_URL_NOT_SET)));
      return;
    }

    createIdmRequest(
            WebClient.create(vertxContext.owner()),
            idmUrl,
            idmToken,
            firstname,
            lastname,
            dateOfBirth)
        .send()
        .map(this::toResponse)
        .onComplete(asyncResultHandler);
  }
}
