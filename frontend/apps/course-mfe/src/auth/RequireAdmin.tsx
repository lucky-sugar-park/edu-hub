import { Alert, Container } from "@mui/material";
import type { ReactNode } from "react";
import { useAuth } from "./AuthContext";
import { RequireAuth } from "./RequireAuth";

/** 로그인이 필요하고, ADMIN이 아니면 안내를 보여 준다. */
export function RequireAdmin({ children }: { children: ReactNode }) {
  const { isAdmin } = useAuth();
  return (
    <RequireAuth>
      {isAdmin ? (
        children
      ) : (
        <Container maxWidth="md" sx={{ py: 4 }}>
          <Alert severity="warning">관리자만 볼 수 있습니다.</Alert>
        </Container>
      )}
    </RequireAuth>
  );
}
