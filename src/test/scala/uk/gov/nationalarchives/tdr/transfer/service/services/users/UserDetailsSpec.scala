package uk.gov.nationalarchives.tdr.transfer.service.services.users

import uk.gov.nationalarchives.tdr.transfer.service.{ApplicationConfig, BaseSpec}

class UserDetailsSpec extends BaseSpec {
  private val authConfig = ApplicationConfig.Auth("http://localhost:9002/auth", "tdr", "user-read-client-id", "user-read-client-secret")
  "'getUserDetails'" should "return the correct user details" in {
    authOk
    keycloakGetUser
    val userDetails = new UserDetails(keycloakCreateAdminClient, authConfig)

    val userRepresentation = userDetails.getUserRepresentation(keycloakUserId)
    userRepresentation.getFirstName should be("FirstName")
    userRepresentation.getLastName should be("LastName")
    userRepresentation.getEmail should be("firstName.lastName@something.com")
    userRepresentation.getId should be(keycloakUserId)
  }

  "'getUserDetails'" should "throw a run time exception if no user representation found" in {
    authOk
    val nonExistentUserId = "nonExistentUserId"
    val userDetails = new UserDetails(keycloakCreateAdminClient, authConfig)

    val exception = intercept[RuntimeException] {
      userDetails.getUserRepresentation(nonExistentUserId)
    }
    exception.getMessage should equal(s"No valid user found $nonExistentUserId: HTTP 404 Not Found")
  }
}
