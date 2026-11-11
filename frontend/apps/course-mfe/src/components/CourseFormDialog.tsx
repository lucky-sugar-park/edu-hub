import type { CourseRequest } from "@eduhub/api-client";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Stack,
  TextField,
} from "@mui/material";
import { type FormEvent, useEffect, useState } from "react";
import { CATEGORIES } from "../categories";

export interface CourseFormDialogProps {
  open: boolean;
  /** 수정할 때의 기존 값. 없으면 등록 폼이다. */
  initial?: CourseRequest;
  submitting: boolean;
  error: string | null;
  onSubmit: (request: CourseRequest) => void;
  onClose: () => void;
}

const EMPTY: CourseRequest = { title: "", category: "", description: "" };

export function CourseFormDialog({
  open,
  initial,
  submitting,
  error,
  onSubmit,
  onClose,
}: CourseFormDialogProps) {
  const [form, setForm] = useState<CourseRequest>(EMPTY);

  useEffect(() => {
    if (open) setForm({ ...EMPTY, ...initial, description: initial?.description ?? "" });
  }, [open, initial]);

  // 임시 분류 목록에 없는 기존 값도 고를 수 있게 남겨 둔다.
  const categories =
    form.category && !CATEGORIES.includes(form.category)
      ? [...CATEGORIES, form.category]
      : CATEGORIES;
  const canSubmit = form.title.trim() !== "" && form.category !== "" && !submitting;

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (!canSubmit) return;
    const description = form.description?.trim();
    onSubmit({
      title: form.title.trim(),
      category: form.category,
      description: description ? description : undefined,
    });
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <form onSubmit={handleSubmit} noValidate>
        <DialogTitle>{initial ? "과정 수정" : "과정 등록"}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            {error && <Alert severity="error">{error}</Alert>}
            <TextField
              label="제목"
              required
              value={form.title}
              onChange={(event) => setForm({ ...form, title: event.target.value })}
              slotProps={{ htmlInput: { maxLength: 200 } }}
            />
            <TextField
              select
              label="분류"
              required
              value={form.category}
              onChange={(event) => setForm({ ...form, category: event.target.value })}
            >
              {categories.map((name) => (
                <MenuItem key={name} value={name}>
                  {name}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              label="설명"
              multiline
              minRows={3}
              value={form.description}
              onChange={(event) => setForm({ ...form, description: event.target.value })}
              slotProps={{ htmlInput: { maxLength: 2000 } }}
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
