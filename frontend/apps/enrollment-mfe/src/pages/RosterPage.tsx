import type { CompletionResponse } from "@eduhub/api-client";
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  CircularProgress,
  Container,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  List,
  ListItem,
  ListItemText,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import { useEffect, useRef, useState } from "react";
import { Link as RouterLink, useParams } from "react-router-dom";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { formatDateTime } from "../format";
import { useCompletion } from "../hooks/useCompletion";
import {
  SessionForbiddenError,
  SessionNotFoundError,
  useSessionRoster,
} from "../hooks/useSessionRoster";
import { statusLabel } from "../statusLabels";
import { enrollmentStatusLabel, failureReasonLabel } from "./labels";

/** 한 번에 수료 처리할 수 있는 최대 인원. 백엔드 CompletionRequest의 @Size(max = 200)과 맞춘다. */
const MAX_SELECTION = 200;

/** 관리자의 회차 명단과 수료 처리(FR-08, D-28). */
export default function RosterPage() {
  const { sessionId } = useParams();
  // 숫자가 아니면 NaN이 되고, 훅이 요청 없이 "회차 없음"으로 처리한다.
  const id = Number(sessionId);
  const { data, loading, error, reload } = useSessionRoster(id);
  const completion = useCompletion();
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [staleNotice, setStaleNotice] = useState<string | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [result, setResult] = useState<CompletionResponse | null>(null);

  const confirmed = data?.confirmed ?? [];
  const waiting = data?.waiting ?? [];
  const selectable = confirmed.filter((entry) => entry.status === "CONFIRMED");
  const completedCount = confirmed.filter((entry) => entry.status === "COMPLETED").length;
  const closed = data?.sessionStatus === "CLOSED";
  const notFound = error instanceof SessionNotFoundError;
  const forbidden = error instanceof SessionForbiddenError;
  // 받아 오는 중이거나 실패한 동안은 명단이 서버 상태와 다를 수 있어 수료 처리를 막는다.
  const unavailable = loading || error !== null || data === null;

  // 효과 안에서 최신 값을 읽기 위한 ref(의존성에 넣으면 선택할 때마다 효과가 다시 돈다).
  const latest = useRef({ selected, confirmOpen, completionError: completion.error });
  latest.current = { selected, confirmOpen, completionError: completion.error };

  // 다시 불러온 명단에서 이미 수료됐거나 사라진 사번은 선택에서 뺀다.
  useEffect(() => {
    if (!data) {
      setSelected((prev) => (prev.size === 0 ? prev : new Set()));
      return;
    }
    const valid = new Set(
      (data.confirmed ?? []).filter((e) => e.status === "CONFIRMED").map((e) => e.employeeId),
    );
    const prev = latest.current.selected;
    const next = new Set([...prev].filter((employeeId) => valid.has(employeeId)));
    if (next.size === prev.size) return;
    setSelected(next);
    // 확인 대화 상자가 열린 채 선택이 모두 사라지면(수료 실패 뒤 다시 불러온 경우 등) 이유를 남긴다.
    if (next.size === 0 && latest.current.confirmOpen) {
      const reason = latest.current.completionError;
      setStaleNotice(
        `선택한 사번이 더 이상 수료 처리 대상이 아닙니다. 명단을 확인해 주세요${reason ? ` (${reason})` : ""}`,
      );
    }
  }, [data]);

  // 상한 때문에 전부 선택할 수 없는 회차에서는 "상한까지 선택됨"을 전체 선택으로 본다.
  const allSelected =
    selectable.length > 0 && selected.size >= Math.min(selectable.length, MAX_SELECTION);
  const limitReached = selected.size >= MAX_SELECTION;
  const someSelected = selected.size > 0 && !allSelected;

  function toggle(employeeId: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(employeeId)) next.delete(employeeId);
      else if (next.size < MAX_SELECTION) next.add(employeeId);
      return next;
    });
  }

  function toggleAll() {
    if (allSelected) {
      setSelected(new Set());
      return;
    }
    // 아직 선택하지 않은 확정자를 앞에서부터 골라 합계가 상한이 될 때까지 더한다.
    const next = new Set(selected);
    for (const entry of selectable) {
      if (next.size >= MAX_SELECTION) break;
      next.add(entry.employeeId ?? "");
    }
    setSelected(next);
  }

  // 확인 대화 상자를 연 채 명단이 다시 불러와져 선택이 0명이 되면 대화 상자를 닫는다.
  useEffect(() => {
    if (confirmOpen && selected.size === 0 && !completion.submitting) {
      completion.clearError();
      setConfirmOpen(false);
    }
  }, [confirmOpen, selected.size, completion.submitting, completion.clearError]);

  function openConfirm() {
    completion.clearError();
    setStaleNotice(null);
    setConfirmOpen(true);
  }

  function closeConfirm() {
    completion.clearError();
    setConfirmOpen(false);
  }

  async function handleComplete() {
    const response = await completion.complete(id, [...selected]);
    if (!response) {
      // 실패(409, 503 등)해도 서버 상태와 맞추려고 다시 불러온다. 오류는 대화 상자 안에 남긴다.
      reload();
      return;
    }
    setConfirmOpen(false);
    setResult(response);
  }

  function closeResult() {
    setResult(null);
    setSelected(new Set());
    reload();
  }

  const completeDisabled = !closed || selected.size === 0 || unavailable || completion.submitting;
  const completeReason = !closed
    ? ""
    : unavailable
      ? error
        ? "명단을 불러오지 못했습니다"
        : "명단을 확인하는 중입니다"
      : "";

  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Button component={RouterLink} to="/admin" sx={{ mb: 2 }}>
        ← 과정 관리
      </Button>

      {loading && !data && (
        <Box sx={{ display: "flex", justifyContent: "center", py: 6 }}>
          <CircularProgress aria-label="불러오는 중" />
        </Box>
      )}

      {notFound && <Alert severity="warning">회차를 찾을 수 없습니다.</Alert>}
      {forbidden && <Alert severity="error">권한이 없습니다.</Alert>}
      {staleNotice && (
        <Alert severity="warning" onClose={() => setStaleNotice(null)} sx={{ mb: 2 }}>
          {staleNotice}
        </Alert>
      )}

      {error && !notFound && !forbidden && (
        <Alert
          severity="error"
          sx={{ mb: 2 }}
          action={
            <Button color="inherit" size="small" onClick={reload}>
              다시 시도
            </Button>
          }
        >
          명단을 불러오지 못했습니다
        </Alert>
      )}

      {data && (
        <>
          <Typography variant="h5" component="h1" gutterBottom>
            {data.courseTitle}
          </Typography>
          <Stack direction="row" spacing={1} sx={{ mb: 1, alignItems: "center", flexWrap: "wrap" }}>
            <Typography>{formatDateTime(data.startAt)}</Typography>
            <Typography>· {data.place}</Typography>
            <Typography>· 정원 {data.capacity}명</Typography>
            <Chip
              label={statusLabel(data.sessionStatus)}
              size="small"
              color={closed ? "default" : "primary"}
            />
          </Stack>
          <Typography color="text.secondary" sx={{ mb: 3 }}>
            확정 {selectable.length}명 · 수료 {completedCount}명 · 대기 {waiting.length}명
          </Typography>

          <Typography variant="h6" component="h2" gutterBottom>
            확정·수료
          </Typography>
          <Paper variant="outlined" sx={{ mb: 2 }}>
            <TableContainer>
              <Table size="small" aria-label="확정·수료 명단">
                <TableHead>
                  <TableRow>
                    <TableCell padding="checkbox">
                      <Checkbox
                        checked={allSelected}
                        indeterminate={someSelected}
                        disabled={selectable.length === 0}
                        onChange={toggleAll}
                        slotProps={{ input: { "aria-label": "확정자 전체 선택" } }}
                      />
                    </TableCell>
                    <TableCell>사번</TableCell>
                    <TableCell>상태</TableCell>
                    <TableCell>확정 시각</TableCell>
                    <TableCell>수료 시각</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {confirmed.map((entry) => {
                    const employeeId = entry.employeeId ?? "";
                    return (
                      <TableRow key={entry.enrollmentId ?? employeeId}>
                        <TableCell padding="checkbox">
                          <Checkbox
                            checked={selected.has(employeeId)}
                            disabled={
                              entry.status !== "CONFIRMED" ||
                              (limitReached && !selected.has(employeeId))
                            }
                            onChange={() => toggle(employeeId)}
                            slotProps={{ input: { "aria-label": `${employeeId} 선택` } }}
                          />
                        </TableCell>
                        <TableCell>{employeeId}</TableCell>
                        <TableCell>{enrollmentStatusLabel(entry.status)}</TableCell>
                        <TableCell>{formatDateTime(entry.confirmedAt)}</TableCell>
                        <TableCell>{formatDateTime(entry.completedAt)}</TableCell>
                      </TableRow>
                    );
                  })}
                  {confirmed.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={5} align="center">
                        확정된 신청이 없습니다.
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          </Paper>

          {selectable.length > MAX_SELECTION && (
            <Alert severity="info" sx={{ mb: 1 }}>
              한 번에 최대 {MAX_SELECTION}명까지 수료 처리할 수 있습니다
            </Alert>
          )}
          <Stack direction="row" spacing={2} sx={{ mb: 4, alignItems: "center" }}>
            <Button
              variant="contained"
              disabled={completeDisabled}
              aria-label={completeReason ? `수료 처리 (${completeReason})` : undefined}
              aria-describedby={closed ? undefined : "roster-complete-hint"}
              onClick={openConfirm}
            >
              수료 처리
            </Button>
            {!closed && (
              <Typography id="roster-complete-hint" variant="body2" color="text.secondary">
                회차를 종료한 뒤 수료 처리할 수 있습니다
              </Typography>
            )}
          </Stack>

          <Typography variant="h6" component="h2" gutterBottom>
            대기
          </Typography>
          <Paper variant="outlined">
            <TableContainer>
              <Table size="small" aria-label="대기 명단">
                <TableHead>
                  <TableRow>
                    <TableCell>순번</TableCell>
                    <TableCell>사번</TableCell>
                    <TableCell>신청 일시</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {waiting.map((entry) => (
                    <TableRow key={entry.enrollmentId ?? entry.employeeId}>
                      <TableCell>{entry.waitingNumber}</TableCell>
                      <TableCell>{entry.employeeId}</TableCell>
                      <TableCell>{formatDateTime(entry.appliedAt)}</TableCell>
                    </TableRow>
                  ))}
                  {waiting.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={3} align="center">
                        대기자가 없습니다.
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          </Paper>
        </>
      )}

      <ConfirmDialog
        open={confirmOpen}
        title="수료 처리"
        message={`선택한 ${selected.size}명을 수료 처리합니다. 수료 처리는 되돌릴 수 없습니다.`}
        confirmLabel="수료 처리"
        submitting={completion.submitting}
        error={completion.error}
        onConfirm={() => void handleComplete()}
        onClose={closeConfirm}
      />

      <Dialog
        open={result !== null}
        onClose={closeResult}
        aria-labelledby="completion-result-title"
      >
        <DialogTitle id="completion-result-title">수료 처리 결과</DialogTitle>
        <DialogContent>
          <Typography variant="subtitle2">수료됨 {result?.completed?.length ?? 0}명</Typography>
          <List dense aria-label="수료된 사번">
            {(result?.completed ?? []).map((employeeId) => (
              <ListItem key={employeeId}>
                <ListItemText primary={employeeId} />
              </ListItem>
            ))}
          </List>
          {(result?.failed?.length ?? 0) > 0 && (
            <>
              <Typography variant="subtitle2">실패 {result?.failed?.length}명</Typography>
              <List dense aria-label="실패한 사번">
                {(result?.failed ?? []).map((failure) => (
                  <ListItem key={failure.employeeId}>
                    <ListItemText
                      primary={failure.employeeId}
                      secondary={failureReasonLabel(failure.reason)}
                    />
                  </ListItem>
                ))}
              </List>
            </>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={closeResult}>확인</Button>
        </DialogActions>
      </Dialog>
    </Container>
  );
}
