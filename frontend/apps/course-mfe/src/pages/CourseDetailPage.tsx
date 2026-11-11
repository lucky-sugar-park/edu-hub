import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Container,
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
import { Link as RouterLink, useParams } from "react-router-dom";
import { formatDateTime } from "../format";
import { CourseNotFoundError, useCourse } from "../hooks/useCourse";
import { statusLabel } from "../statusLabels";

export function CourseDetailPage() {
  const { id } = useParams();
  // 숫자가 아니면 NaN이 되고, 훅이 요청 없이 "과정 없음"으로 처리한다.
  const { data: course, loading, error } = useCourse(Number(id));
  const sessions = course?.sessions ?? [];

  return (
    <Container maxWidth="md" sx={{ py: 4 }}>
      <Button component={RouterLink} to="/" sx={{ mb: 2 }}>
        ← 목록으로
      </Button>

      {loading && (
        <Box sx={{ display: "flex", justifyContent: "center", py: 6 }}>
          <CircularProgress aria-label="불러오는 중" />
        </Box>
      )}

      {!loading && error instanceof CourseNotFoundError && (
        <Alert severity="warning">과정을 찾을 수 없습니다. 삭제되었거나 주소가 잘못되었습니다.</Alert>
      )}

      {!loading && error && !(error instanceof CourseNotFoundError) && (
        <Alert severity="error">과정 정보를 불러오지 못했습니다.</Alert>
      )}

      {!loading && course && (
        <>
          <Typography variant="h5" component="h1" gutterBottom>
            {course.title}
          </Typography>
          <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
            <Chip label={course.category} size="small" variant="outlined" />
            <Chip
              label={statusLabel(course.status)}
              size="small"
              color={course.status === "OPEN" ? "primary" : "default"}
            />
          </Stack>
          <Typography sx={{ whiteSpace: "pre-line", mb: 4 }}>{course.description}</Typography>

          <Typography variant="h6" component="h2" gutterBottom>
            회차
          </Typography>
          <Paper variant="outlined">
            <TableContainer>
              <Table aria-label="회차 목록">
                <TableHead>
                  <TableRow>
                    <TableCell>일정</TableCell>
                    <TableCell>장소</TableCell>
                    <TableCell align="right">정원</TableCell>
                    <TableCell align="right">남은 자리</TableCell>
                    <TableCell>상태</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {sessions.map((session) => (
                    <TableRow key={session.id}>
                      <TableCell>{formatDateTime(session.startAt)}</TableCell>
                      <TableCell>{session.place}</TableCell>
                      <TableCell align="right">{session.capacity}</TableCell>
                      <TableCell align="right">{session.remainingSeats}</TableCell>
                      <TableCell>{statusLabel(session.status)}</TableCell>
                    </TableRow>
                  ))}
                  {sessions.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={5} align="center">
                        등록된 회차가 없습니다.
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          </Paper>
        </>
      )}
    </Container>
  );
}
