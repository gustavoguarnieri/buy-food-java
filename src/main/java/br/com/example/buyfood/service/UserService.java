package br.com.example.buyfood.service;

import br.com.example.buyfood.constants.ErrorMessages;
import br.com.example.buyfood.enums.Role;
import br.com.example.buyfood.exception.BusinessException;
import br.com.example.buyfood.exception.ConflitException;
import br.com.example.buyfood.exception.NotFoundException;
import br.com.example.buyfood.model.dto.request.UserCreateRequestDTO;
import br.com.example.buyfood.model.dto.request.UserSigninRequestDTO;
import br.com.example.buyfood.model.dto.request.UserUpdateRequestDTO;
import br.com.example.buyfood.model.dto.response.UserCreateResponseDTO;
import br.com.example.buyfood.model.dto.response.UserResponseDTO;
import br.com.example.buyfood.model.entity.UserEntity;
import br.com.example.buyfood.model.repository.UserRepository;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.keycloak.KeycloakPrincipal;
import org.keycloak.OAuth2Constants;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.Configuration;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserService {

  @Value("${keycloak.realm}")
  private String realm;

  @Value("${keycloak.auth-server-url}")
  private String authServerUrl;

  @Value("${keycloak.resource}")
  private String clientId;

  @Value("${keycloak.credentials.secret}")
  private String clientSecret;

  @Value("${keycloak-custom.admin-user}")
  private String adminUser;

  @Value("${keycloak-custom.admin-password}")
  private String adminPass;

  @Autowired private ModelMapper modelMapper;

  @Autowired private UserRepository userRepository;

  private static final int KEYCLOAK_CONNECTION_POOL_SIZE = 15;

  public UserResponseDTO getUser(String userId) {
    var userEntity =
        userRepository
            .findByUserId(userId)
            .orElseThrow(() -> new NotFoundException(ErrorMessages.USER_NOT_FOUND));

    return convertToDto(userEntity);
  }

  public UserCreateResponseDTO createUser(UserCreateRequestDTO userCreateRequestDto) {
    var userEntity = saveCustomUser(userCreateRequestDto);

    var role = userCreateRequestDto.getRole().name().toLowerCase();

    var keycloak = getKeycloakBuilder(adminUser, adminPass);

    var user = new UserRepresentation();
    user.setEnabled(true);
    user.setUsername(userCreateRequestDto.getEmail());
    user.setFirstName(userCreateRequestDto.getFirstName());
    user.setLastName(userCreateRequestDto.getLastName());
    user.setEmail(userCreateRequestDto.getEmail());

    var realmResource = keycloak.realm(realm);
    var usersResource = realmResource.users();

    var userCreateResponseDto = new UserCreateResponseDTO();

    try (var response = usersResource.create(user)) {

      userCreateResponseDto.setStatusCode(response.getStatus());
      userCreateResponseDto.setStatus(response.getStatusInfo().toString());

      if (response.getStatus() == HttpStatus.CREATED.value()) {
        var userId = CreatedResponseUtil.getCreatedId(response);
        userCreateResponseDto.setUserId(userId);

        log.info(
            "createUser: Created user userId={} userMail={}",
            userId,
            userCreateRequestDto.getEmail());

        var passwordCred = getCredentialRepresentation(userCreateRequestDto.getPassword());

        var userResource = usersResource.get(userId);
        userResource.resetPassword(passwordCred);

        insertNewRole(role, realmResource, userResource);

        userEntity.setUserId(userId);
        userEntity.getAudit().setCreatedBy(userId);
        userRepository.save(userEntity);

      } else {
        deleteCustomUser(userEntity.getUserId());
        log.warn(
            "createUser: Unexpected status code for userMail= {} status={}",
            user.getEmail(),
            response.getStatus() + "-" + response.getStatusInfo().toString());
      }

    } catch (Exception ex) {
      log.error("createUser: An error occurred when creating the user={} ", user.getUsername(), ex);
    }

    return userCreateResponseDto;
  }

  private void insertNewRole(
      String newRole, RealmResource realmResource, UserResource userResource) {
    if (newRole.isBlank()) {
      return;
    }

    RoleRepresentation realmRoleUser;
    try {
      log.info("insertNewRole: Available roles: " + realmResource.roles().list());
      realmRoleUser = realmResource.roles().get(newRole).toRepresentation();
      userResource.roles().realmLevel().add(Collections.singletonList(realmRoleUser));
      removeOldRoles(newRole, realmResource, userResource);
    } catch (Exception ex) {
      log.error("insertNewRole: An error occurred when get role={}", newRole.toLowerCase(), ex);
    }
  }

  private void removeOldRoles(
      String newRole, RealmResource realmResource, UserResource userResource) {
    List<RoleRepresentation> roleRepresentationList = new ArrayList<>();

    Role.stream()
        .filter(i -> !i.name().equals(newRole.toUpperCase()))
        .forEach(
            i -> {
              var realmRoleUser = realmResource.roles().get(i.name()).toRepresentation();
              roleRepresentationList.add(realmRoleUser);
            });

    userResource.roles().realmLevel().remove(roleRepresentationList);
  }

  private CredentialRepresentation getCredentialRepresentation(String password) {
    var passwordCred = new CredentialRepresentation();
    passwordCred.setTemporary(false);
    passwordCred.setType(CredentialRepresentation.PASSWORD);
    passwordCred.setValue(password);
    return passwordCred;
  }

  private Keycloak getKeycloakBuilder(String user, String pass) {
    return KeycloakBuilder.builder()
        .serverUrl(authServerUrl)
        .grantType(OAuth2Constants.PASSWORD)
        .realm(realm)
        .clientId(clientId)
        .clientSecret(clientSecret)
        .username(user)
        .password(pass)
        .resteasyClient(
            new ResteasyClientBuilder().connectionPoolSize(KEYCLOAK_CONNECTION_POOL_SIZE).build())
        .build();
  }

  public AccessTokenResponse signin(UserSigninRequestDTO userSignin) {
    var clientCredentials = getClientCredentials();

    AccessTokenResponse response = null;
    try {
      var configuration =
          new Configuration(authServerUrl, realm, clientId, clientCredentials, null);
      var authzClient = AuthzClient.create(configuration);
      response = authzClient.obtainAccessToken(userSignin.getEmail(), userSignin.getPassword());
    } catch (Exception ex) {
      log.error("signin: An error occurred when signing user={}", userSignin.getEmail(), ex);
    }
    return response;
  }

  private Map<String, Object> getClientCredentials() {
    Map<String, Object> clientCredentials = new HashMap<>();
    clientCredentials.put("secret", clientSecret);
    clientCredentials.put("grant_type", CredentialRepresentation.PASSWORD);
    return clientCredentials;
  }

  public Optional<String> getUserId() {
    try {
      var keycloakClaims = getKeycloakClaims();
      return keycloakClaims == null
          ? Optional.empty()
          : Optional.ofNullable(keycloakClaims.get("user_id").toString());
    } catch (Exception ex) {
      log.error("getUserId: An error occurred when getUserId, user ", ex);
      return Optional.empty();
    }
  }

  private Map<String, Object> getKeycloakClaims() {

    if (SecurityContextHolder.getContext().getAuthentication()
        instanceof AnonymousAuthenticationToken) {
      return null;
    }

    KeycloakAuthenticationToken authentication =
        (KeycloakAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();

    var principal = (Principal) authentication.getPrincipal();

    var keycloakPrincipal = (KeycloakPrincipal) principal;
    var token = keycloakPrincipal.getKeycloakSecurityContext().getToken();
    return token.getOtherClaims();
  }

  private UserEntity saveCustomUser(UserCreateRequestDTO userCreateRequestDto) {

    var user = userRepository.findByEmail(userCreateRequestDto.getEmail());

    if (user.isPresent()) {
      log.warn(
          "saveCustomUser: Duplicated resource, this email={} already exist ",
          userCreateRequestDto.getEmail());
      throw new ConflitException("Duplicated resource, this email already exist");
    }

    try {
      var userEntity = convertToEntity(userCreateRequestDto);
      return userRepository.save(userEntity);
    } catch (Exception ex) {
      log.error(
          "saveCustomUser: An error occurred when save user={} ",
          userCreateRequestDto.getEmail(),
          ex);
      throw new BusinessException(ex.getMessage());
    }
  }

  public void updateCustomUser(String userId, UserUpdateRequestDTO userUpdateRequestDto) {

    var optionalUserEntity = userRepository.findByUserId(userId);

    var userEntity = optionalUserEntity.orElseGet(UserEntity::new);

    try {
      userEntity.setUserId(userId);
      userEntity.setEmail(userUpdateRequestDto.getEmail());
      userEntity.setFirstName(userUpdateRequestDto.getFirstName());
      userEntity.setLastName(userUpdateRequestDto.getLastName());
      userEntity.setNickName(userUpdateRequestDto.getNickName());
      userEntity.setPhone(userUpdateRequestDto.getPhone());
      userEntity.getAudit().setLastUpdatedBy(userId);
      userRepository.save(userEntity);
    } catch (Exception ex) {
      log.error("updateCustomUser: An error occurred when update userId={} ", userId, ex);
      throw new BusinessException(ex.getMessage());
    }

    try {
      var keycloak = getKeycloakBuilder(adminUser, adminPass);
      var realmResource = keycloak.realm(realm);
      var usersResource = realmResource.users();
      var userResource = usersResource.get(userId);

      var user = userResource.toRepresentation();
      user.setFirstName(userUpdateRequestDto.getFirstName());
      user.setLastName(userUpdateRequestDto.getLastName());

      CredentialRepresentation passwordCred;
      if (!userUpdateRequestDto.getPassword().isBlank()) {
        passwordCred = getCredentialRepresentation(userUpdateRequestDto.getPassword());
        userResource.resetPassword(passwordCred);
      }

      usersResource.get(userId).update(user);

      insertNewRole(userUpdateRequestDto.getRole().toLowerCase(), realmResource, userResource);
    } catch (Exception ex) {
      log.error(
          "updateCustomUser: An error occurred when update keycloak user={} ",
          userEntity.getEmail(),
          ex);
      throw new BusinessException(ex.getMessage());
    }
  }

  public void deleteCustomUser(String userId) {
    var userEntity =
        userRepository
            .findByUserId(userId)
            .orElseThrow(() -> new NotFoundException(ErrorMessages.USER_NOT_FOUND));

    try {
      userEntity.setStatus(0);
      userRepository.save(userEntity);
    } catch (Exception ex) {
      log.error("deleteCustomUser: An error occurred when update userId={} ", userId, ex);
      throw new BusinessException(ex.getMessage());
    }

    try {
      var keycloak = getKeycloakBuilder(adminUser, adminPass);
      var realmResource = keycloak.realm(realm);
      var usersResource = realmResource.users();

      var user = usersResource.get(userEntity.getUserId()).toRepresentation();
      user.setEnabled(false);
      usersResource.get(userEntity.getUserId()).update(user);
    } catch (Exception ex) {
      log.error(
          "deleteCustomUser: An error occurred when update keycloak user={} ",
          userEntity.getEmail(),
          ex);
      throw new BusinessException(ex.getMessage());
    }
  }

  private UserResponseDTO convertToDto(UserEntity userEntity) {
    return modelMapper.map(userEntity, UserResponseDTO.class);
  }

  private UserEntity convertToEntity(Object userCreateRequestDto) {
    return modelMapper.map(userCreateRequestDto, UserEntity.class);
  }
}
