package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import java.util.List;

/**
 * Representação pública de uma página de usuários (SPI-62), no contrato de
 * paginação do ADR-28 (page/size zero-based).
 */
public record UserPageResponse(
    List<UserResponse> content,
    long totalElements,
    int page,
    int size,
    int totalPages
) {

    public static UserPageResponse from(PageResult<User> result) {
        return new UserPageResponse(
            result.content().stream().map(UserResponse::from).toList(),
            result.totalElements(),
            result.page(),
            result.size(),
            result.totalPages()
        );
    }
}
