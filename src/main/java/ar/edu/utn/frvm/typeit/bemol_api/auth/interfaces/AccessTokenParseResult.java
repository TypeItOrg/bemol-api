package ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces;

import io.jsonwebtoken.Claims;

public sealed interface AccessTokenParseResult
    permits AccessTokenParseResult.Ok,
        AccessTokenParseResult.Expired,
        AccessTokenParseResult.Invalid {

  record Ok(Claims claims) implements AccessTokenParseResult {}

  record Expired() implements AccessTokenParseResult {}

  record Invalid() implements AccessTokenParseResult {}
}
