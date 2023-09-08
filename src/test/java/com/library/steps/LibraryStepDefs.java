package com.library.steps;

import com.github.javafaker.Faker;
import com.library.pages.BookPage;
import com.library.pages.LoginPage;
import com.library.utility.BrowserUtil;
import com.library.utility.DB_Util;
import com.library.utility.Driver;
import com.library.utility.LibraryAPI_Util;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.Assert;
import org.openqa.selenium.Keys;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

public class ApiStepDefs {


    String token; //API Token for logging in
    RequestSpecification requestSpecification1; //make it global for adding the contentType header
    Response response; //Global response variable for sharing response results between methods
    String pathParam; //pathParam to share between methods
    Map<String, Object> randomBook = new HashMap<>();
    LoginPage loginPage = new LoginPage();
    BookPage bookPage = new BookPage();
    JsonPath jsonPath;
    String password;

    @Given("I logged Library api as a {string}")
    public void i_logged_library_api_as_a(String usertype) {

        token = LibraryAPI_Util.getToken(usertype);

    }
    @Given("Accept header is {string}")
    public void accept_header_is(String contentType) {

        requestSpecification1 = given().accept(contentType);

    }
    @When("I send GET request to {string} endpoint")
    public void i_send_get_request_to_endpoint(String endPoint) {

        response = requestSpecification1.and().header("x-library-token", token)
                .when().get(endPoint);

    }
    @Then("status code should be {int}")
    public void status_code_should_be(Integer statusCode) {

        response.then().statusCode(statusCode);
    }
    @Then("Response Content type is {string}")
    public void response_content_type_is(String contentType) {

        response.then().contentType(contentType);
    }
    @Then("{string} field should not be null")
    public void field_should_not_be_null(String field) {

        response.then().body(field, notNullValue());


    }

    /**
     *
     * USER STORY 2
     *
     */

    @Given("Path param is {string}")
    public void path_param_is(String pathParam) {

        requestSpecification1.and().pathParam("id", pathParam);
        this.pathParam=pathParam; //Move to global variable
    }
    @Then("{string} field should be same with path param")
    public void field_should_be_same_with_path_param(String field) {
        response.then().body(field, is(equalTo(pathParam)));
    }
    @Then("following fields should not be null")
    public void following_fields_should_not_be_null(List<String> dataTypes) {
        for (String dataType : dataTypes) {
            response.then().body(dataType, is(notNullValue()));
        }
    }


    /**
     *
     * USER STORY 3
     *
     */


    @Given("Request Content Type header is {string}")
    public void request_content_type_header_is(String header) {

        //Ensure this header is accurate
        requestSpecification1.and().header("Content-Type", header);
    }
    @Given("I create a random {string} as request body")
    public void i_create_a_random_as_request_body(String object) {

        Faker faker = new Faker();

        if(object.equals("book")) {

            randomBook.put("name", faker.book().title());
            randomBook.put("isbn", faker.number().randomNumber(12,false));
            randomBook.put("year", faker.number().numberBetween(1850, 2023));
            randomBook.put("author", faker.book().author());
            randomBook.put("book_category_id",faker.number().numberBetween(1,20));
            randomBook.put("description",faker.chuckNorris().fact());



            requestSpecification1.and()
                    .formParam("name", randomBook.get("name"))
                    .formParam("isbn", randomBook.get("isbn"))
                    .formParam("year", randomBook.get("year"))
                    .formParam("author", randomBook.get("author"))
                    .formParam("book_category_id",randomBook.get("book_category_id"))
                    .formParam("description",randomBook.get("description"));

        }
        else if(object.equals("user")){

            password=faker.internet().password();
            requestSpecification1.and()
                    .formParam("full_name", faker.name().fullName())
                    .formParam("email", faker.internet().emailAddress())
                    .formParam("password", password)
                    .formParam("user_group_id", 2)
                    .formParam("status",faker.ancient().god())
                    .formParam("start_date","2023-03-11")
                    .formParam("end_date", "2023-10-1")
                    .formParam("address",faker.address().fullAddress());
        }



    }
    @When("I send POST request to {string} endpoint")
    public void i_send_post_request_to_endpoint(String endpoint) {

        response = requestSpecification1.and().header("x-library-token", token).when().post(endpoint).prettyPeek();
    }
    @Then("the field value for {string} path should be equal to {string}")
    public void the_field_value_for_path_should_be_equal_to(String expectedField, String actualField) {

        String expected = response.jsonPath().getString(expectedField);

        Assert.assertEquals(expected, actualField);

    }

    @Given("I logged in Library UI as {string}")
    public void i_logged_in_library_ui_as(String userType) {
        loginPage.login(userType);
    }
    @Given("I navigate to {string} page")
    public void i_navigate_to_page(String moduleName) {
        bookPage.navigateModule(moduleName);
    }
    @Then("UI, Database and API created book information must match")
    public void ui_database_and_api_created_book_information_must_match() {

        //Get book_id from the response
        String bookID = response.jsonPath().getString("book_id");

        //Call API to get API book info
        JsonPath jsonPath = given().contentType(ContentType.JSON).pathParam("id", bookID)
                .and().header("x-library-token", token)
                        .when().get("/get_book_by_id/{id}").prettyPeek()
                .then().statusCode(200).extract().jsonPath();
        String apiBookName = jsonPath.getString("name");
        String apiAuthorName = jsonPath.getString("author"); //Get author for searching in UI

        //Get UI book info
        bookPage.search.sendKeys(apiAuthorName+ Keys.ENTER); //Search UI so element is visible
        bookPage.editBookByID(bookID).click(); //Open the book info
        String uiBookName = bookPage.bookName.getAttribute("value"); //Get the book name

        //Get DB book info
        String query = "SELECT name from books where id = "+bookID;
        DB_Util.runQuery(query);
        String dbBookName = DB_Util.getFirstRowFirstColumn();

        //Perform assertions
        Assert.assertEquals(uiBookName, apiBookName);
        Assert.assertEquals(apiBookName, dbBookName);


    }

    //USER STORY 4

    @Then("created user information should match with Database")
    public void created_user_information_should_match_with_database() {

        //Get user_id from the response
        String userID = response.jsonPath().getString("user_id");

        //Call API to get API book info
        jsonPath = given().contentType(ContentType.JSON).pathParam("id", userID)
                .and().header("x-library-token", token)
                .when().get("/get_user_by_id/{id}").prettyPeek()
                .then().statusCode(200).extract().jsonPath();
        String apiUserName = jsonPath.getString("full_name");

        //Get DB book info
        String query = "SELECT full_name from users where id = "+userID;
        DB_Util.runQuery(query);
        String dbUserName = DB_Util.getFirstRowFirstColumn();

        //Perform assertions
        Assert.assertEquals(apiUserName, dbUserName);

    }
    @Then("created user should be able to login Library UI")
    public void created_user_should_be_able_to_login_library_ui() {

        String email = jsonPath.getString("email");

        BrowserUtil.waitForVisibility(loginPage.loginButton, 20);
        loginPage.login(email, password);

        BrowserUtil.waitFor(3);
        Assert.assertTrue(bookPage.accountHolderName.isDisplayed());
        System.out.println("Login verified....");

    }
    @Then("created user name should appear in Dashboard Page")
    public void created_user_name_should_appear_in_dashboard_page() {


        String actualUserFullName = bookPage.accountHolderName.getText();
        String expectedUserFullName = jsonPath.getString("full_name");

        System.out.println("actualUserFullName = " + actualUserFullName);
        System.out.println("expectedUserFullName = " + expectedUserFullName);

        Assert.assertEquals(expectedUserFullName, actualUserFullName);
    }

    //USER STORY 5

    @Given("I logged Library api with credentials {string} and {string}")
    public void i_logged_library_api_with_credentials_and(String email, String password) {

        token = LibraryAPI_Util.getToken(email, password);
    }
    @Given("I send token information as request body")
    public void i_send_token_information_as_request_body() {

        requestSpecification1.and().formParam("token", token);
    }

}
