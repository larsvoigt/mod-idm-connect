package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Index.atIndex;
import static org.folio.rest.impl.Constants.BASE_PATH_CONTRACTS;

import com.google.common.io.Resources;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.parsing.Parser;
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Contract;
import org.folio.rest.jaxrs.model.Contract.Status;
import org.folio.rest.jaxrs.model.Contracts;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.ModuleName;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class IdmConnectApiIT {

  private static final String HOST = "http://localhost";
  private static final String TENANT = "diku";
  private static final Map<String, String> OKAPI_HEADERS = Map.of("x-okapi-tenant", TENANT);
  private static final String CONTRACT_JSON = "examplecontract.json";
  private static final Vertx vertx = Vertx.vertx();

  @BeforeClass
  public static void beforeClass(TestContext context) {
    int port = NetworkUtils.nextFreePort();
    RestAssured.reset();
    RestAssured.baseURI = HOST;
    RestAssured.port = port;
    RestAssured.defaultParser = Parser.JSON;
    RestAssured.requestSpecification =
        new RequestSpecBuilder()
            .setBasePath(BASE_PATH_CONTRACTS)
            .addHeaders(OKAPI_HEADERS)
            .addHeader("Content-Type", "application/json")
            .build();

    PostgresClient.setPostgresTester(new PostgresTesterContainer());

    DeploymentOptions options =
        new DeploymentOptions().setConfig(new JsonObject().put("http.port", port));

    vertx.deployVerticle(RestVerticle.class.getName(), options, context.asyncAssertSuccess());
  }

  @AfterClass
  public static void afterClass() {
    RestAssured.reset();
  }

  @Test
  public void testThatWeCanGetPostPutAndDelete(TestContext context) throws IOException {
    Promise<AsyncResult<Response>> promise = Promise.promise();
    new CustomTenantApi()
        .postTenantSync(
            new TenantAttributes().withModuleTo(ModuleName.getModuleVersion()),
            OKAPI_HEADERS,
            promise::complete,
            vertx.getOrCreateContext());
    promise.future().onComplete(context.asyncAssertSuccess());

    String jsonStr =
        Resources.toString(Resources.getResource(CONTRACT_JSON), StandardCharsets.UTF_8);
    Contract expectedContract = Json.decodeValue(jsonStr, Contract.class);

    // POST
    Contract postResult =
        given().body(expectedContract).post().then().statusCode(201).extract().as(Contract.class);
    assertThat(postResult)
        .hasFieldOrProperty("id")
        .hasFieldOrProperty("metadata")
        .usingRecursiveComparison()
        .ignoringFields("id", "metadata")
        .isEqualTo(expectedContract);

    // GET
    Contracts getResult = given().get().then().statusCode(200).extract().as(Contracts.class);
    assertThat(getResult)
        .satisfies(
            contracts -> {
              assertThat(contracts.getContracts())
                  .hasSize(1)
                  .satisfies(
                      actualContract ->
                          assertThat(actualContract)
                              .hasFieldOrProperty("id")
                              .hasFieldOrProperty("metadata")
                              .usingRecursiveComparison()
                              .ignoringFields("id", "metadata")
                              .isEqualTo(expectedContract),
                      atIndex(0));
              assertThat(contracts.getTotalRecords()).isEqualTo(1);
            });

    // GET by id
    Contract getByIdResult =
        given()
            .pathParam("id", postResult.getId())
            .get("/{id}")
            .then()
            .statusCode(200)
            .extract()
            .as(Contract.class);
    assertThat(getByIdResult).usingRecursiveComparison().isEqualTo(postResult);

    // PUT modified entity
    given()
        .pathParam("id", getByIdResult.getId())
        .body(getByIdResult.withStatus(Status.ACTIVATED))
        .put("/{id}")
        .then()
        .statusCode(204);

    // GET by id
    Contract getByIdResult2 =
        given()
            .pathParam("id", postResult.getId())
            .get("/{id}")
            .then()
            .statusCode(200)
            .extract()
            .as(Contract.class);
    assertThat(getByIdResult2)
        .usingRecursiveComparison()
        .ignoringFields("status", "metadata.updatedDate")
        .isEqualTo(postResult);
    assertThat(getByIdResult2.getStatus()).isEqualTo(Status.ACTIVATED);

    // DELETE
    given().pathParam("id", getByIdResult.getId()).delete("/{id}").then().statusCode(204);

    // GET
    Contracts getResult2 = given().get().then().statusCode(200).extract().as(Contracts.class);
    assertThat(getResult2)
        .satisfies(
            contracts -> {
              assertThat(contracts.getContracts()).isEmpty();
              assertThat(contracts.getTotalRecords()).isZero();
            });

    // GET by id
    given().pathParam("id", postResult.getId()).get("/{id}").then().statusCode(404);
  }
}
