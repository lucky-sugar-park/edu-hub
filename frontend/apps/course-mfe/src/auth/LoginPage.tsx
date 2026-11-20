import { Alert, Box, Button, Container, Paper, TextField, Typography } from "@mui/material";
import { type FormEvent, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { DevLoginUnavailableError, useAuth } from "./AuthContext";
import { toErrorMessage } from "../api/errors";

const SSO_MESSAGE = "이 환경에서는 사내 SSO 연동 전까지 로그인할 수 없습니다";

/** 돌아올 경로는 앱 안의 경로만 받는다(오픈 리다이렉트 방지). 백슬래시·제어문자·/login은 거부한다. */
function safeFrom(state: unknown): string {
  const from = (state as { from?: unknown } | null)?.from;
  if (typeof from !== "string" || !from.startsWith("/") || from.startsWith("//")) return "/";
  if ([...from].some((c) => c === "\\" || c.charCodeAt(0) < 32 || c.charCodeAt(0) === 127))
    return "/";
  try {
    const url = new URL(from, window.location.origin);
    if (url.origin !== window.location.origin || url.pathname.replace(/\/+$/, "") === "/login")
      return "/";
  } catch {
    return "/";
  }
  return from;
}

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [employeeId, setEmployeeId] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const id = employeeId.trim();
    if (!id) {
      setError("사번을 입력하세요.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await login(id);
      navigate(safeFrom(location.state), { replace: true });
    } catch (cause) {
      setError(cause instanceof DevLoginUnavailableError ? SSO_MESSAGE : toErrorMessage(cause));
      setSubmitting(false);
    }
  }

  return (
    <Container maxWidth="xs" sx={{ py: 6 }}>
      <Paper sx={{ p: 3 }}>
        <Typography variant="h6" component="h1" gutterBottom>
          로그인
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          개발 환경 전용 로그인(비밀번호 없음)
        </Typography>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        <Box component="form" onSubmit={handleSubmit} noValidate>
          <TextField
            label="사번"
            value={employeeId}
            onChange={(event) => setEmployeeId(event.target.value)}
            fullWidth
            autoFocus
            slotProps={{ htmlInput: { maxLength: 20 } }}
            sx={{ mb: 2 }}
          />
          <Button type="submit" variant="contained" fullWidth disabled={submitting}>
            로그인
          </Button>
        </Box>
      </Paper>
    </Container>
  );
}
