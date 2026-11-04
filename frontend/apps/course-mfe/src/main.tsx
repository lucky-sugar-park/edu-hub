import { CssBaseline } from "@mui/material";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { CourseListPage } from "./CourseListPage";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <CssBaseline />
    <CourseListPage />
  </StrictMode>,
);
