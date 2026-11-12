import type { SessionRequest, SessionResponse } from "@eduhub/api-client";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
} from "@mui/material";
import { type FormEvent, useEffect, useState } from "react";
import { fromLocalInputValue, toLocalInputValue } from "../dateTime";

export interface SessionFormDialogProps {
  open: boolean;
  /** 수정할 회차. 없으면 등록 폼이다. */
  session?: SessionResponse;
  /** 교육이 시작된 회차면 정원과 시작 시각을 바꿀 수 없다(D-08). */
  started: boolean;
  submitting: boolean;
  error: string | null;
  onSubmit: (request: SessionRequest) => void;
  onClose: () => void;
}

interface FormState {
  startAt: string;
  place: string;
  capacity: string;
}

const EMPTY: FormState = { startAt: "", place: "", capacity: "" };

export function SessionFormDialog({
  open,
  session,
  started,
  submitting,
  error,
  onSubmit,
  onClose,
}: SessionFormDialogProps) {
  const [form, setForm] = useState<FormState>(EMPTY);

  useEffect(() => {
    if (!open) return;
    setForm(
      session
        ? {
            startAt: session.startAt ? toLocalInputValue(session.startAt) : "",
            place: session.place ?? "",
            capacity: String(session.capacity ?? ""),
          }
        : EMPTY,
    );
  }, [open, session]);

  const capacity = Number(form.capacity);
  const canSubmit =
    form.startAt !== "" &&
    form.place.trim() !== "" &&
    Number.isInteger(capacity) &&
    capacity >= 1 &&
    !submitting;
  const lockedHelp = started ? "교육이 시작되어 바꿀 수 없습니다" : undefined;

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (!canSubmit) return;
    onSubmit({
      // 시작된 회차는 원래 값을 그대로 보내 분 단위 반올림으로 값이 바뀌지 않게 한다.
      startAt: started && session?.startAt ? session.startAt : fromLocalInputValue(form.startAt),
      place: form.place.trim(),
      capacity,
    });
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <form onSubmit={handleSubmit} noValidate>
        <DialogTitle>{session ? "회차 수정" : "회차 등록"}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            {error && <Alert severity="error">{error}</Alert>}
            <TextField
              label="시작 시각"
              type="datetime-local"
              required
              disabled={started}
              helperText={lockedHelp}
              value={form.startAt}
              onChange={(event) => setForm({ ...form, startAt: event.target.value })}
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <TextField
              label="장소"
              required
              value={form.place}
              onChange={(event) => setForm({ ...form, place: event.target.value })}
              slotProps={{ htmlInput: { maxLength: 200 } }}
            />
            <TextField
              label="정원"
              type="number"
              required
              disabled={started}
              helperText={lockedHelp ?? "1~1000명"}
              value={form.capacity}
              onChange={(event) => setForm({ ...form, capacity: event.target.value })}
              slotProps={{ htmlInput: { min: 1, max: 1000 } }}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>취소</Button>
          <Button type="submit" variant="contained" disabled={!canSubmit}>
            저장
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
