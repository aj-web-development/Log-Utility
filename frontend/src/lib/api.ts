import { getAuthHeader, clearStoredAuth } from "./authStorage";
import type {
  ApiErrorBody,
  LogbackParseResult,
  PathCheckOutcome,
  PathCheckRequest,
  ProjectDetailResponse,
  ProjectRequest,
  ProjectSummaryDto,
  PublicProjectView,
  SampleLineAnalysis,
  SearchRequest,
  SearchResult,
} from "./types";

export class ApiRequestError extends Error {
  constructor(
    public status: number,
    public body: ApiErrorBody | null,
    fallbackMessage: string,
  ) {
    super(body?.message ?? fallbackMessage);
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const needsAuth = path.startsWith("/api/projects");
  const headers = new Headers(init.headers);
  const isFormData = init.body instanceof FormData;
  if (!isFormData && init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (needsAuth) {
    const auth = getAuthHeader();
    if (auth) headers.set("Authorization", auth);
  }

  const res = await fetch(path, { ...init, headers });

  if (res.status === 401 && needsAuth) {
    clearStoredAuth();
    if (!location.pathname.startsWith("/login")) location.href = "/login";
  }

  if (!res.ok) {
    let body: ApiErrorBody | null = null;
    try {
      body = await res.json();
    } catch {
      // Non-JSON error body (e.g. a 404 the servlet container produced, not our handler).
    }
    throw new ApiRequestError(res.status, body, `Request failed with status ${res.status}`);
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return text ? (JSON.parse(text) as T) : (undefined as T);
}

function toQuery(params: Record<string, string | undefined>): string {
  const usp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) if (v) usp.set(k, v);
  return usp.toString();
}

/** Query params shared by GET /api/search/stream and GET /api/search/export. */
export interface SearchStreamParams {
  projectId: string;
  from?: string;
  to?: string;
  freeText?: string;
  filters?: Record<string, string>;
}

export function searchStreamQuery(params: SearchStreamParams): string {
  const flat: Record<string, string> = {
    projectId: params.projectId,
    from: params.from,
    to: params.to,
    freeText: params.freeText,
  } as Record<string, string>;
  for (const [k, v] of Object.entries(params.filters ?? {})) if (v) flat[`filter_${k}`] = v;
  return toQuery(flat);
}

export const searchApi = {
  projects: () => request<ProjectSummaryDto[]>("/api/search/projects"),
  project: (id: string) => request<PublicProjectView>(`/api/search/projects/${id}`),
  search: (body: SearchRequest) => request<SearchResult>("/api/search", { method: "POST", body: JSON.stringify(body) }),
  streamUrl: (params: SearchStreamParams) => `/api/search/stream?${searchStreamQuery(params)}`,
  exportUrl: (params: SearchStreamParams) => `/api/search/export?${searchStreamQuery(params)}`,
};

export const projectApi = {
  list: () => request<ProjectSummaryDto[]>("/api/projects"),
  get: (id: string) => request<ProjectDetailResponse>(`/api/projects/${id}`),
  create: (body: ProjectRequest) => request<ProjectDetailResponse>("/api/projects", { method: "POST", body: JSON.stringify(body) }),
  update: (id: string, body: ProjectRequest) =>
    request<ProjectDetailResponse>(`/api/projects/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  remove: (id: string) => request<void>(`/api/projects/${id}`, { method: "DELETE" }),
  parseLogback: (file: File) => {
    const form = new FormData();
    form.set("file", file);
    return request<LogbackParseResult>("/api/projects/logback/parse", { method: "POST", body: form });
  },
  analyzeSampleLine: (sampleLine: string) =>
    request<SampleLineAnalysis>("/api/projects/sample-line/analyze", {
      method: "POST",
      body: JSON.stringify({ sampleLine }),
    }),
  checkPath: (body: PathCheckRequest) =>
    request<PathCheckOutcome>("/api/projects/path-check", { method: "POST", body: JSON.stringify(body) }),
};
