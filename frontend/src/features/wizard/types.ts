import type { CheckStatus, FilterFieldRequest, LinePatternRequest, LogFileRequest, ProjectDetailResponse, ProjectRequest } from "@/lib/types";

let keySeq = 0;
export function newKey(): string {
  keySeq += 1;
  return `k${keySeq}`;
}

export interface WizardLogFile extends LogFileRequest {
  _key: string;
  logFileId?: string;
  lastCheckStatus?: CheckStatus;
  lastCheckMessage?: string | null;
}

export interface WizardNode {
  _key: string;
  logSourceId?: string;
  nodeLabel: string;
  logFiles: WizardLogFile[];
}

export interface WizardField extends FilterFieldRequest {
  _key: string;
}

export interface WizardState {
  name: string;
  description: string;
  nodes: WizardNode[];
  fields: WizardField[];
  linePattern: LinePatternRequest;
  sampleLine: string;
}

export function emptyLogFile(): WizardLogFile {
  return { _key: newKey(), fileLabel: "", liveLogPath: "", backupRootPath: "", backupPathPattern: "" };
}

export function emptyNode(): WizardNode {
  return { _key: newKey(), nodeLabel: "", logFiles: [emptyLogFile()] };
}

export function emptyField(): WizardField {
  return { _key: newKey(), key: "", label: "", mdcKey: "", matchType: "EXACT_TOKEN", linePrefix: "" };
}

export function emptyWizardState(): WizardState {
  return {
    name: "",
    description: "",
    nodes: [emptyNode()],
    fields: [],
    linePattern: { timestampPattern: "", timestampRegexOrPosition: "", levelPattern: "", loggerPattern: "" },
    sampleLine: "",
  };
}

export function wizardStateFromDetail(detail: ProjectDetailResponse): WizardState {
  return {
    name: detail.name,
    description: detail.description ?? "",
    nodes: detail.nodes.length
      ? detail.nodes.map((n) => ({
          _key: newKey(),
          logSourceId: n.logSourceId,
          nodeLabel: n.nodeLabel,
          logFiles: n.logFiles.length
            ? n.logFiles.map((f) => ({
                _key: newKey(),
                logFileId: f.logFileId,
                fileLabel: f.fileLabel,
                liveLogPath: f.liveLogPath,
                backupRootPath: f.backupRootPath,
                backupPathPattern: f.backupPathPattern,
                lastCheckStatus: f.lastCheckStatus,
                lastCheckMessage: f.lastCheckMessage,
              }))
            : [emptyLogFile()],
        }))
      : [emptyNode()],
    fields: detail.filterFields.map((f) => ({ ...f, _key: newKey() })),
    linePattern: detail.linePattern ?? { timestampPattern: "", timestampRegexOrPosition: "", levelPattern: "", loggerPattern: "" },
    sampleLine: "",
  };
}

export function wizardStateToRequest(state: WizardState): ProjectRequest {
  return {
    name: state.name,
    description: state.description || null,
    nodes: state.nodes
      .filter((n) => n.nodeLabel.trim() !== "")
      .map((n) => ({
        nodeLabel: n.nodeLabel,
        logFiles: n.logFiles
          .filter((f) => f.fileLabel.trim() !== "" || f.liveLogPath.trim() !== "")
          .map((f) => ({
            fileLabel: f.fileLabel,
            liveLogPath: f.liveLogPath,
            backupRootPath: f.backupRootPath,
            backupPathPattern: f.backupPathPattern,
          })),
      })),
    filterFields: state.fields
      .filter((f) => f.key.trim() !== "")
      .map((f) => ({ key: f.key, label: f.label, mdcKey: f.mdcKey || null, matchType: f.matchType, linePrefix: f.linePrefix || null })),
    linePattern: state.linePattern,
  };
}
