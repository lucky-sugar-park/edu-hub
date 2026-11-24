import { Alert, Box, CircularProgress, Container } from "@mui/material";
import { Component, type ErrorInfo, type ReactNode, Suspense } from "react";

interface BoundaryProps {
  /** 사용자에게 보여 줄 remote 이름(예: "과정 화면"). */
  name: string;
  /** 회차 행 안처럼 좁은 자리면 true. 안내를 작게 보여 준다. */
  inline?: boolean;
  children: ReactNode;
}

interface BoundaryState {
  failed: boolean;
}

/** remote를 불러오거나 그리다 실패하면(remote 서버가 꺼짐 등) 화면 전체 대신 안내만 보여 준다. */
class RemoteErrorBoundary extends Component<BoundaryProps, BoundaryState> {
  state: BoundaryState = { failed: false };

  static getDerivedStateFromError(): BoundaryState {
    return { failed: true };
  }

  componentDidCatch(error: unknown, info: ErrorInfo) {
    // 오류 객체에는 요청 config(토큰)가 들어 있을 수 있어 메시지만 남긴다.
    const message = error instanceof Error ? error.message : String(error);
    console.error(`[remote] ${this.props.name} 실패: ${message}`, info.componentStack);
  }

  render() {
    if (!this.state.failed) return this.props.children;
    const alert = (
      <Alert severity="error">
        {this.props.name}을(를) 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.
      </Alert>
    );
    return this.props.inline ? (
      alert
    ) : (
      <Container maxWidth="md" sx={{ py: 4 }}>
        {alert}
      </Container>
    );
  }
}

/** remote 화면을 Suspense(불러오는 중)와 오류 경계로 감싼다. */
export function RemoteBoundary({ name, inline = false, children }: BoundaryProps) {
  const loading = inline ? (
    <CircularProgress size={20} aria-label={`${name} 불러오는 중`} />
  ) : (
    <Box sx={{ display: "flex", justifyContent: "center", py: 6 }}>
      <CircularProgress aria-label={`${name} 불러오는 중`} />
    </Box>
  );
  return (
    <RemoteErrorBoundary name={name} inline={inline}>
      <Suspense fallback={loading}>{children}</Suspense>
    </RemoteErrorBoundary>
  );
}
