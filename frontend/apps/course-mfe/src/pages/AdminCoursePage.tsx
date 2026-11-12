import type { CourseRequest, SessionRequest, SessionResponse } from "@eduhub/api-client";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Container,
  Link,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  Typography,
} from "@mui/material";
import { useMemo, useState } from "react";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { CourseFormDialog } from "../components/CourseFormDialog";
import { SessionFormDialog } from "../components/SessionFormDialog";
import { formatDateTime, hasStarted } from "../dateTime";
import { useCourseAdmin } from "../hooks/useCourseAdmin";
import { useCourseDetail } from "../hooks/useCourseDetail";
import { useCourses } from "../hooks/useCourses";

const COURSE_STATUS_LABELS: Record<string, string> = { OPEN: "운영 중", CLOSED: "종료" };
const SESSION_STATUS_LABELS: Record<string, string> = { OPEN: "진행", CLOSED: "종료(교육 완료)" };

type DialogState =
  | { kind: "none" }
  | { kind: "createCourse" }
  | { kind: "editCourse" }
  | { kind: "closeCourse" }
  | { kind: "createSession" }
  | { kind: "editSession"; session: SessionResponse }
  | { kind: "closeSession"; session: SessionResponse };

/** 관리자의 과정·회차 등록, 수정, 종료 화면(FR-03). */
export function AdminCoursePage() {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [dialog, setDialog] = useState<DialogState>({ kind: "none" });

  const courses = useCourses({ keyword: "", category: "", page, size });
  const detail = useCourseDetail(selectedId);
  const admin = useCourseAdmin();

  const course = detail.data;
  const courseOpen = course?.status === "OPEN";
  const courseInitial = useMemo<CourseRequest | undefined>(
    () =>
      course
        ? {
            title: course.title ?? "",
            category: course.category ?? "",
            description: course.description,
          }
        : undefined,
    [course],
  );

  const openDialog = (next: DialogState) => {
    admin.clearError();
    setDialog(next);
  };
  const closeDialog = () => setDialog({ kind: "none" });

  /** 요청이 성공하면 대화 상자를 닫고 목록과 상세를 다시 불러온다. 실패하면 대화 상자에 에러를 보여 준다. */
  const finish = (result: unknown) => {
    if (result === null) return;
    closeDialog();
    courses.reload();
    detail.reload();
  };

  const handleCourseSubmit = async (request: CourseRequest) => {
    if (dialog.kind === "editCourse" && selectedId !== null) {
      finish(await admin.updateCourse(selectedId, request));
      return;
    }
    const created = await admin.createCourse(request);
    if (created?.id !== undefined) setSelectedId(created.id);
    finish(created);
  };

  const handleSessionSubmit = async (request: SessionRequest) => {
    if (dialog.kind === "editSession" && dialog.session.id !== undefined) {
      finish(await admin.updateSession(dialog.session.id, request));
    } else if (selectedId !== null) {
      finish(await admin.createSession(selectedId, request));
    }
  };

  const handleConfirm = async () => {
    if (dialog.kind === "closeCourse" && selectedId !== null) {
      finish(await admin.closeCourse(selectedId));
    } else if (dialog.kind === "closeSession" && dialog.session.id !== undefined) {
      finish(await admin.closeSession(dialog.session.id));
    }
  };

  const rows = courses.data?.content ?? [];
  const sessions = course?.sessions ?? [];

  return (
    <Container maxWidth="md" sx={{ py: 4 }}>
      <Stack direction="row" sx={{ mb: 2, alignItems: "center", justifyContent: "space-between" }}>
        <Typography variant="h5" component="h1">
          과정 관리
        </Typography>
        <Button variant="contained" onClick={() => openDialog({ kind: "createCourse" })}>
          과정 등록
        </Button>
      </Stack>

      {courses.error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          과정 목록을 불러오지 못했습니다.
        </Alert>
      )}
      {courses.loading && <Loading label="과정 목록 불러오는 중" />}
      {!courses.loading && !courses.error && (
        <Paper variant="outlined" sx={{ mb: 3 }}>
          <TableContainer>
            <Table size="small" aria-label="과정 목록">
              <TableHead>
                <TableRow>
                  <TableCell>제목</TableCell>
                  <TableCell>분류</TableCell>
                  <TableCell>상태</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((row) => (
                  <TableRow key={row.id} hover selected={row.id === selectedId}>
                    <TableCell>
                      <Link
                        component="button"
                        type="button"
                        underline="hover"
                        onClick={() => setSelectedId(row.id ?? null)}
                      >
                        {row.title}
                      </Link>
                    </TableCell>
                    <TableCell>{row.category}</TableCell>
                    <TableCell>{COURSE_STATUS_LABELS[row.status ?? ""] ?? row.status}</TableCell>
                  </TableRow>
                ))}
                {rows.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={3} align="center">
                      과정이 없습니다.
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination
            component="div"
            count={courses.data?.totalElements ?? 0}
            page={page}
            rowsPerPage={size}
            rowsPerPageOptions={[10, 20, 50]}
            labelRowsPerPage="페이지당 개수"
            onPageChange={(_, nextPage) => setPage(nextPage)}
            onRowsPerPageChange={(event) => {
              setSize(Number(event.target.value));
              setPage(0);
            }}
          />
        </Paper>
      )}

      {selectedId === null && !courses.loading && (
        <Typography color="text.secondary">과정을 선택하면 회차를 관리할 수 있습니다.</Typography>
      )}
      {detail.error && <Alert severity="error">과정 상세를 불러오지 못했습니다.</Alert>}
      {detail.loading && <Loading label="과정 상세 불러오는 중" />}
      {course && (
        <Paper variant="outlined" sx={{ p: 2 }} component="section" aria-label="선택한 과정">
          <Stack
            direction={{ xs: "column", sm: "row" }}
            spacing={1}
            sx={{ mb: 1, justifyContent: "space-between" }}
          >
            <Box>
              <Typography variant="h6" component="h2">
                {course.title}{" "}
                <Chip
                  size="small"
                  label={COURSE_STATUS_LABELS[course.status ?? ""] ?? course.status}
                  color={courseOpen ? "primary" : "default"}
                />
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {course.category}
              </Typography>
            </Box>
            <Stack direction="row" spacing={1} sx={{ alignItems: "flex-start" }}>
              <Button disabled={!courseOpen} onClick={() => openDialog({ kind: "editCourse" })}>
                과정 수정
              </Button>
              <Button
                color="error"
                disabled={!courseOpen}
                onClick={() => openDialog({ kind: "closeCourse" })}
              >
                과정 종료
              </Button>
            </Stack>
          </Stack>
          {course.description && (
            <Typography variant="body2" sx={{ mb: 2, whiteSpace: "pre-line" }}>
              {course.description}
            </Typography>
          )}
          {!courseOpen && (
            <Alert severity="info" sx={{ mb: 2 }}>
              종료된 과정은 수정하거나 회차를 추가할 수 없습니다. 남은 회차는 교육이 끝난 뒤 종료합니다.
            </Alert>
          )}

          <Stack direction="row" sx={{ mb: 1, alignItems: "center", justifyContent: "space-between" }}>
            <Typography variant="subtitle1" component="h3">
              회차
            </Typography>
            <Button
              variant="outlined"
              size="small"
              disabled={!courseOpen}
              onClick={() => openDialog({ kind: "createSession" })}
            >
              회차 등록
            </Button>
          </Stack>
          <TableContainer>
            <Table size="small" aria-label="회차 목록">
              <TableHead>
                <TableRow>
                  <TableCell>시작 시각</TableCell>
                  <TableCell>장소</TableCell>
                  <TableCell align="right">정원</TableCell>
                  <TableCell>상태</TableCell>
                  <TableCell align="right">작업</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {sessions.map((session) => {
                  const sessionOpen = session.status === "OPEN";
                  const started = hasStarted(session.startAt);
                  return (
                    <TableRow key={session.id}>
                      <TableCell>{formatDateTime(session.startAt)}</TableCell>
                      <TableCell>{session.place}</TableCell>
                      <TableCell align="right">{session.capacity}</TableCell>
                      <TableCell>
                        {SESSION_STATUS_LABELS[session.status ?? ""] ?? session.status}
                        {sessionOpen && started && " · 교육 시작됨"}
                      </TableCell>
                      <TableCell align="right" sx={{ whiteSpace: "nowrap" }}>
                        <Button
                          size="small"
                          disabled={!sessionOpen}
                          onClick={() => openDialog({ kind: "editSession", session })}
                        >
                          수정
                        </Button>
                        <Button
                          size="small"
                          color="error"
                          disabled={!sessionOpen || !started}
                          title={started ? undefined : "교육 시작 후에 종료할 수 있습니다"}
                          onClick={() => openDialog({ kind: "closeSession", session })}
                        >
                          종료
                        </Button>
                      </TableCell>
                    </TableRow>
                  );
                })}
                {sessions.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5} align="center">
                      회차가 없습니다.
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </Paper>
      )}

      <CourseFormDialog
        open={dialog.kind === "createCourse" || dialog.kind === "editCourse"}
        initial={dialog.kind === "editCourse" ? courseInitial : undefined}
        submitting={admin.submitting}
        error={admin.error}
        onSubmit={handleCourseSubmit}
        onClose={closeDialog}
      />
      <SessionFormDialog
        open={dialog.kind === "createSession" || dialog.kind === "editSession"}
        session={dialog.kind === "editSession" ? dialog.session : undefined}
        started={dialog.kind === "editSession" && hasStarted(dialog.session.startAt)}
        submitting={admin.submitting}
        error={admin.error}
        onSubmit={handleSessionSubmit}
        onClose={closeDialog}
      />
      <ConfirmDialog
        open={dialog.kind === "closeCourse" || dialog.kind === "closeSession"}
        title={dialog.kind === "closeCourse" ? "과정 종료" : "회차 종료"}
        message={
          dialog.kind === "closeCourse"
            ? "과정을 종료하면 수정하거나 회차를 추가할 수 없습니다. 등록된 회차는 그대로 남습니다."
            : "회차 종료는 교육 완료를 뜻하며 되돌릴 수 없습니다."
        }
        confirmLabel="종료"
        submitting={admin.submitting}
        error={admin.error}
        onConfirm={handleConfirm}
        onClose={closeDialog}
      />
    </Container>
  );
}

function Loading({ label }: { label: string }) {
  return (
    <Box sx={{ display: "flex", justifyContent: "center", py: 4 }}>
      <CircularProgress aria-label={label} />
    </Box>
  );
}
