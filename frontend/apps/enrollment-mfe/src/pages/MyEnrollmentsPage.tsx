import {
  type GetMyEnrollmentsStatusEnum,
  GetMyEnrollmentsStatusEnum as StatusValues,
  type MyEnrollmentResponse,
} from "@eduhub/api-client";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Container,
  Link,
  Paper,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  Tabs,
  Tooltip,
  Typography,
} from "@mui/material";
import { useEffect, useState } from "react";
import { Link as RouterLink, useSearchParams } from "react-router-dom";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { formatDateTime } from "../format";
import { useEnrollmentActions } from "../hooks/useEnrollmentActions";
import { useMyEnrollments } from "../hooks/useMyEnrollments";
import { enrollmentStatusLabel } from "./labels";

const PAGE_SIZE = 20;
const ALL = "ALL";
const TABS: { value: string; label: string }[] = [
  { value: ALL, label: "전체" },
  { value: StatusValues.Confirmed, label: enrollmentStatusLabel("CONFIRMED") },
  { value: StatusValues.Waiting, label: enrollmentStatusLabel("WAITING") },
  { value: StatusValues.Cancelled, label: enrollmentStatusLabel("CANCELLED") },
  { value: StatusValues.Completed, label: enrollmentStatusLabel("COMPLETED") },
];

function parseStatus(value: string | null): GetMyEnrollmentsStatusEnum | undefined {
  return Object.values(StatusValues).find((status) => status === value);
}

function parsePage(value: string | null): number {
  const page = Number(value);
  return Number.isSafeInteger(page) && page > 0 ? page : 0;
}

function hasStarted(enrollment: MyEnrollmentResponse, now: number): boolean {
  return enrollment.sessionStartAt !== undefined && Date.parse(enrollment.sessionStartAt) <= now;
}

/**
 * 내 신청 내역(FR-07). 탭(status)과 페이지는 URL 쿼리(?status=WAITING&page=1)에 둔다.
 * 새로고침과 뒤로가기에서 상태가 유지되고, 주소를 그대로 공유할 수 있다.
 */
export default function MyEnrollmentsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const status = parseStatus(searchParams.get("status"));
  const page = parsePage(searchParams.get("page"));
  const { data, loading, error, reload } = useMyEnrollments(status, page, PAGE_SIZE);
  const actions = useEnrollmentActions();
  const [notice, setNotice] = useState<string | null>(null);
  const [cancelTarget, setCancelTarget] = useState<MyEnrollmentResponse | null>(null);

  const rows = data?.content ?? [];

  // 범위를 벗어난 page(?page=99)나 비어 버린 마지막 페이지는 마지막 유효 페이지로 옮긴다.
  useEffect(() => {
    if (loading || !data || page === 0 || (data.content ?? []).length > 0) return;
    const lastPage = Math.max(0, Math.ceil((data.totalElements ?? 0) / PAGE_SIZE) - 1);
    if (lastPage >= page) return;
    const params = new URLSearchParams(searchParams);
    if (lastPage > 0) params.set("page", String(lastPage));
    else params.delete("page");
    setSearchParams(params, { replace: true });
  }, [loading, data, page, searchParams, setSearchParams]);
  const now = Date.now();
  // 받아 오는 중이거나 실패한 동안은 목록이 서버 상태와 다를 수 있어 취소를 막는다.
  const unavailable = loading || error !== null;

  function changeStatus(next: string) {
    const params = new URLSearchParams();
    if (next !== ALL) params.set("status", next);
    setNotice(null);
    setSearchParams(params);
  }

  function changePage(next: number) {
    const params = new URLSearchParams(searchParams);
    if (next > 0) params.set("page", String(next));
    else params.delete("page");
    setSearchParams(params);
  }

  function openCancel(enrollment: MyEnrollmentResponse) {
    actions.clearError();
    setNotice(null);
    setCancelTarget(enrollment);
  }

  function closeCancel() {
    actions.clearError();
    setCancelTarget(null);
  }

  async function handleCancel() {
    if (!cancelTarget || cancelTarget.sessionId === undefined) return;
    const result = await actions.cancel(cancelTarget.sessionId);
    // 실패해도 서버 상태와 맞추려고 다시 불러온다.
    reload();
    if (!result) return;
    setCancelTarget(null);
    setNotice("신청이 취소되었습니다");
  }

  function renderAction(enrollment: MyEnrollmentResponse) {
    const active = enrollment.status === "CONFIRMED" || enrollment.status === "WAITING";
    if (!active || hasStarted(enrollment, now)) return null;
    const reason = unavailable
      ? error
        ? "내 신청 내역을 불러오지 못했습니다"
        : "내 신청 내역을 확인하는 중입니다"
      : null;
    return (
      <Tooltip title={reason ?? ""}>
        <span>
          <Button
            size="small"
            color="error"
            aria-label={reason ? `취소 (${reason})` : undefined}
            disabled={reason !== null || actions.submitting}
            onClick={() => openCancel(enrollment)}
          >
            취소
          </Button>
        </span>
      </Tooltip>
    );
  }

  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Typography variant="h5" component="h1" gutterBottom>
        내 신청 내역
      </Typography>

      <Tabs
        value={status ?? ALL}
        onChange={(_, value: string) => changeStatus(value)}
        aria-label="신청 상태"
        sx={{ mb: 2 }}
      >
        {TABS.map((tab) => (
          <Tab key={tab.value} value={tab.value} label={tab.label} />
        ))}
      </Tabs>

      {notice && (
        <Alert severity="success" onClose={() => setNotice(null)} sx={{ mb: 2 }}>
          {notice}
        </Alert>
      )}
      {actions.error && !cancelTarget && (
        <Alert severity="error" onClose={actions.clearError} sx={{ mb: 2 }}>
          {actions.error}
        </Alert>
      )}
      {error && (
        <Alert
          severity="error"
          sx={{ mb: 2 }}
          action={
            <Button color="inherit" size="small" onClick={reload}>
              다시 시도
            </Button>
          }
        >
          내 신청 내역을 불러오지 못했습니다
        </Alert>
      )}

      {loading && !data && (
        <Box sx={{ display: "flex", justifyContent: "center", py: 6 }}>
          <CircularProgress aria-label="불러오는 중" />
        </Box>
      )}

      {data && (
        <Paper variant="outlined">
          <TableContainer>
            <Table aria-label="내 신청 목록">
              <TableHead>
                <TableRow>
                  <TableCell>과정</TableCell>
                  <TableCell>회차 일시</TableCell>
                  <TableCell>장소</TableCell>
                  <TableCell>상태</TableCell>
                  <TableCell>대기 순번</TableCell>
                  <TableCell>신청 일시</TableCell>
                  <TableCell>작업</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((enrollment) => (
                  <TableRow key={enrollment.id}>
                    <TableCell>
                      {enrollment.courseId !== undefined ? (
                        <Link component={RouterLink} to={`/courses/${enrollment.courseId}`}>
                          {enrollment.courseTitle}
                        </Link>
                      ) : (
                        enrollment.courseTitle
                      )}
                    </TableCell>
                    <TableCell>{formatDateTime(enrollment.sessionStartAt)}</TableCell>
                    <TableCell>{enrollment.sessionPlace}</TableCell>
                    <TableCell>
                      <Chip
                        label={enrollmentStatusLabel(enrollment.status)}
                        size="small"
                        color={
                          enrollment.status === "CONFIRMED" || enrollment.status === "COMPLETED"
                            ? "success"
                            : enrollment.status === "WAITING"
                              ? "warning"
                              : "default"
                        }
                      />
                    </TableCell>
                    <TableCell>
                      {enrollment.status === "WAITING" ? `${enrollment.waitingNumber}번` : "-"}
                    </TableCell>
                    <TableCell>{formatDateTime(enrollment.appliedAt)}</TableCell>
                    <TableCell>{renderAction(enrollment)}</TableCell>
                  </TableRow>
                ))}
                {rows.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={7} align="center">
                      <Typography sx={{ mb: 1 }}>신청 내역이 없습니다</Typography>
                      <Button component={RouterLink} to="/" size="small">
                        과정 목록
                      </Button>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination
            component="div"
            count={data.totalElements ?? 0}
            page={page}
            rowsPerPage={PAGE_SIZE}
            rowsPerPageOptions={[PAGE_SIZE]}
            onPageChange={(_, next) => changePage(next)}
          />
        </Paper>
      )}

      <ConfirmDialog
        open={cancelTarget !== null}
        title="신청 취소"
        message={
          cancelTarget?.status === "CONFIRMED"
            ? "확정자가 취소하면 대기 1순위가 확정됩니다. 신청을 취소할까요?"
            : "대기 신청을 취소할까요?"
        }
        confirmLabel="신청 취소"
        submitting={actions.submitting}
        error={actions.error}
        onConfirm={() => void handleCancel()}
        onClose={closeCancel}
      />
    </Container>
  );
}
