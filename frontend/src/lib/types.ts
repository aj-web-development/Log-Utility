// Mirrors the backend's JSON contract exactly (field names/types) - see
// com.app.logutility.request.* / response.* records. Keep in sync with those, not the other way
// around.

export type MatchType = "EXACT_TOKEN" | "SUBSTRING" | "REGEX";
export type CheckStatus = "REACHABLE" | "UNREACHABLE" | "UNKNOWN";

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}

export interface ProjectSummaryDto {
  id: string;
  name: string;
  description: string | null;
  nodeCount: number;
  fieldCount: number;
  updatedAt: string;
}

export interface PublicFilterFieldView {
  key: string;
  label: string;
}

export interface PublicProjectView {
  id: string;
  name: string;
  fields: PublicFilterFieldView[];
}

export interface LogFileRequest {
  fileLabel: string;
  liveLogPath: string;
  backupRootPath: string;
  backupPathPattern: string;
}

export interface LogFileResponse extends LogFileRequest {
  logFileId: string;
  lastCheckStatus: CheckStatus;
  lastCheckMessage: string | null;
}

export interface NodeRequest {
  nodeLabel: string;
  logFiles: LogFileRequest[];
}

export interface NodeResponse {
  logSourceId: string;
  nodeLabel: string;
  logFiles: LogFileResponse[];
}

export interface FilterFieldRequest {
  key: string;
  label: string;
  mdcKey: string | null;
  matchType: MatchType;
  linePrefix: string | null;
}

export type FilterFieldResponse = FilterFieldRequest;

export interface LinePatternRequest {
  timestampPattern: string;
  timestampRegexOrPosition: string;
  levelPattern: string | null;
  loggerPattern: string | null;
}

export type LinePatternResponse = LinePatternRequest;

export interface ProjectRequest {
  name: string;
  description: string | null;
  nodes: NodeRequest[];
  filterFields: FilterFieldRequest[];
  linePattern: LinePatternRequest;
}

export interface ProjectDetailResponse {
  id: string;
  name: string;
  description: string | null;
  nodes: NodeResponse[];
  filterFields: FilterFieldResponse[];
  linePattern: LinePatternResponse | null;
}

export interface PathCheckRequest {
  livePath: string;
  backupPath: string;
  logFileId?: string;
}

export interface PathCheckOutcome {
  status: CheckStatus;
  message: string | null;
}

export interface TokenMatch {
  text: string;
  start: number;
  end: number;
}

export interface HighlightSegment {
  text: string;
  label: string | null;
}

export interface SampleLineAnalysis {
  timestamp: TokenMatch | null;
  suggestedTimestampPattern: string | null;
  suggestedTimestampRegex: string | null;
  level: TokenMatch | null;
  suggestedLevelPattern: string | null;
  logger: TokenMatch | null;
  suggestedLoggerPattern: string | null;
  segments: HighlightSegment[];
}

export interface MdcFieldSuggestion {
  mdcKey: string;
  suggestedKey: string;
  suggestedLabel: string;
  linePrefix: string;
}

export interface LogbackParseResult {
  mdcFields: MdcFieldSuggestion[];
  backupPathPattern: string | null;
  liveLogPathHint: string | null;
  backupRootHint: string | null;
}

export interface SearchRequest {
  projectId: string;
  from: string | null;
  to: string | null;
  filters: Record<string, string>;
  freeText: string | null;
  page: number;
  pageSize: number;
}

export interface LogLine {
  nodeLabel: string;
  fileLabel: string;
  timestamp: string | null;
  level: string | null;
  raw: string;
}

export interface SearchResult {
  lines: LogLine[];
  totalMatched: number;
  truncated: boolean;
  unreachableNodes: string[];
  elapsedMillis: number;
}

export interface SearchSummary {
  totalMatched: number;
  truncated: boolean;
  unreachableNodes: string[];
  elapsedMillis: number;
}

export interface SearchProgress {
  nodeLabel: string;
  nodesCompleted: number;
  nodesTotal: number;
}
