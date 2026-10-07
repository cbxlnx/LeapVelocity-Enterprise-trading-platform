import type { IncomingHttpHeaders } from "node:http";

export interface AuthenticatedUser {
  username: string;
}

export interface AuthenticatedRequest {
  headers: IncomingHttpHeaders;
  user?: AuthenticatedUser;
}
