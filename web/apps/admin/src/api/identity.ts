import type { components } from "@mkt/shared/openapi/admin";
import type { Result } from "@mkt/shared";
import { request } from "./http";

export type AdminUserView = components["schemas"]["AdminUserView"];
export type AdminUserCreateCommand = components["schemas"]["AdminUserCreateCommand"];
export type AdminUserUpdateCommand = components["schemas"]["AdminUserUpdateCommand"];
export type PageDataAdminUserView = components["schemas"]["PageDataAdminUserView"];
export type RoleView = components["schemas"]["RoleView"];
export type RoleCreateCommand = components["schemas"]["RoleCreateCommand"];
export type RoleUpdateCommand = components["schemas"]["RoleUpdateCommand"];
export type RoleAssignPermissionsCommand = components["schemas"]["RoleAssignPermissionsCommand"];
export type PageDataRoleView = components["schemas"]["PageDataRoleView"];
export type PermissionTreeNode = components["schemas"]["PermissionTreeNodeResponse"];
export type SessionView = components["schemas"]["SessionView"];
export type SessionKickCommand = components["schemas"]["SessionKickCommand"];
export type PageDataSessionView = components["schemas"]["PageDataSessionView"];
export type PortalUserView = components["schemas"]["PortalUserView"];
export type PortalUserDetail = components["schemas"]["PortalUserDetailResponse"];
export type PortalUserProfileCommand = components["schemas"]["PortalUserProfileCommand"];
export type PageDataPortalUserView = components["schemas"]["PageDataPortalUserView"];
export type InternalAppView = components["schemas"]["InternalAppView"];
export type InternalAppCreateCommand = components["schemas"]["InternalAppCreateCommand"];
export type InternalAppCreatedResponse = components["schemas"]["InternalAppCreatedResponse"];
export type InternalAppRotateResponse = components["schemas"]["InternalAppRotateResponse"];
export type PageDataInternalAppView = components["schemas"]["PageDataInternalAppView"];
export type IdResponse = components["schemas"]["IdResponse"];
export type OkResponse = components["schemas"]["OkResponse"];

export type PageParams = {
  page?: number;
  pageSize?: number;
};

export function pageUsers(
  params: PageParams & { username?: string; nickname?: string; status?: string; roleId?: number },
): Promise<Result<PageDataAdminUserView>> {
  return request("GET", "/admin/identity/users", undefined, params);
}

export function createUser(body: AdminUserCreateCommand): Promise<Result<IdResponse>> {
  return request("POST", "/admin/identity/users", body);
}

export function updateUser(id: number, body: AdminUserUpdateCommand): Promise<Result<OkResponse>> {
  return request("PUT", `/admin/identity/users/${id}`, body);
}

export function disableUser(id: number): Promise<Result<OkResponse>> {
  return request("POST", `/admin/identity/users/${id}/disable`);
}

export function enableUser(id: number): Promise<Result<OkResponse>> {
  return request("POST", `/admin/identity/users/${id}/enable`);
}

export function resetUserPassword(id: number, newPassword: string): Promise<Result<OkResponse>> {
  return request("POST", `/admin/identity/users/${id}/reset-password`, { newPassword });
}

export function deleteUser(id: number): Promise<Result<OkResponse>> {
  return request("DELETE", `/admin/identity/users/${id}`);
}

export function pageRoles(params: PageParams & { all?: boolean }): Promise<Result<PageDataRoleView>> {
  return request("GET", "/admin/identity/roles", undefined, params);
}

export function createRole(body: RoleCreateCommand): Promise<Result<IdResponse>> {
  return request("POST", "/admin/identity/roles", body);
}

export function updateRole(id: number, body: RoleUpdateCommand): Promise<Result<OkResponse>> {
  return request("PUT", `/admin/identity/roles/${id}`, body);
}

export function deleteRole(id: number): Promise<Result<OkResponse>> {
  return request("DELETE", `/admin/identity/roles/${id}`);
}

export function assignRolePermissions(id: number, body: RoleAssignPermissionsCommand): Promise<Result<OkResponse>> {
  return request("PUT", `/admin/identity/roles/${id}/permissions`, body);
}

export function fetchPermissionTree(): Promise<Result<PermissionTreeNode[]>> {
  return request("GET", "/admin/identity/permissions/tree");
}

export function pageSessions(
  params: PageParams & { accountType?: string; account?: string },
): Promise<Result<PageDataSessionView>> {
  return request("GET", "/admin/identity/sessions", undefined, params);
}

export function kickSession(body: SessionKickCommand): Promise<Result<OkResponse>> {
  return request("POST", "/admin/identity/sessions/kick", body);
}

export function pagePortalUsers(
  params: PageParams & {
    username?: string;
    nickname?: string;
    province?: string;
    level?: string;
    tag?: string;
    status?: string;
    registeredFrom?: string;
    registeredTo?: string;
  },
): Promise<Result<PageDataPortalUserView>> {
  return request("GET", "/admin/identity/portal-users", undefined, params);
}

export function fetchPortalUser(id: number): Promise<Result<PortalUserDetail>> {
  return request("GET", `/admin/identity/portal-users/${id}`);
}

export function updatePortalProfile(id: number, body: PortalUserProfileCommand): Promise<Result<OkResponse>> {
  return request("PUT", `/admin/identity/portal-users/${id}/profile`, body);
}

export function disablePortalUser(id: number): Promise<Result<OkResponse>> {
  return request("POST", `/admin/identity/portal-users/${id}/disable`);
}

export function enablePortalUser(id: number): Promise<Result<OkResponse>> {
  return request("POST", `/admin/identity/portal-users/${id}/enable`);
}

export function resetPortalPassword(id: number, newPassword: string): Promise<Result<OkResponse>> {
  return request("POST", `/admin/identity/portal-users/${id}/reset-password`, { newPassword });
}

export function deletePortalUser(id: number): Promise<Result<OkResponse>> {
  return request("DELETE", `/admin/identity/portal-users/${id}`);
}

export function pageInternalApps(params: PageParams): Promise<Result<PageDataInternalAppView>> {
  return request("GET", "/admin/identity/internal-apps", undefined, params);
}

export function createInternalApp(body: InternalAppCreateCommand): Promise<Result<InternalAppCreatedResponse>> {
  return request("POST", "/admin/identity/internal-apps", body);
}

export function rotateInternalAppSecret(id: number): Promise<Result<InternalAppRotateResponse>> {
  return request("POST", `/admin/identity/internal-apps/${id}/rotate-secret`);
}

export function disableInternalApp(id: number): Promise<Result<OkResponse>> {
  return request("POST", `/admin/identity/internal-apps/${id}/disable`);
}

export function enableInternalApp(id: number): Promise<Result<OkResponse>> {
  return request("POST", `/admin/identity/internal-apps/${id}/enable`);
}
