import { Alert, Box, Button, Container, Paper, TextField, Typography } from "@mui/material";
import { type FormEvent, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { toErrorMessage } from "@eduhub/api-client";

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

/**
 * 사번·비밀번호 로그인(D-33). 비밀번호는 입력란 상태에만 두고, 실패하면 비운다.
 * 사번 없음과 비밀번호 틀림은 백엔드가 같은 401 메시지를 주므로 화면도 구분하지 않는다.
 */
export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [employeeNo, setEmployeeNo] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const id = employeeNo.trim();
    if (!id || !password) {
      setError(!id ? "사번을 입력하세요." : "비밀번호를 입력하세요.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await login(id, password);
      navigate(safeFrom(location.state), { replace: true });
    } catch (cause) {
      setPassword("");
      setError(toErrorMessage(cause));
      setSubmitting(false);
    }
  }

  return (
    <Container maxWidth="xs" sx={{ py: 6 }}>
      <Paper sx={{ p: 3 }}>
        <Typography variant="h6" component="h1" gutterBottom>
          로그인
        </Typography>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        <Box component="form" onSubmit={handleSubmit} noValidate>
          <TextField
            label="사번"
            value={employeeNo}
            onChange={(event) => setEmployeeNo(event.target.value)}
            fullWidth
            autoFocus
            autoComplete="username"
            slotProps={{ htmlInput: { maxLength: 20 } }}
            sx={{ mb: 2 }}
          />
          <TextField
            label="비밀번호"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            fullWidth
            autoComplete="current-password"
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
