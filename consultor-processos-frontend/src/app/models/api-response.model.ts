export interface ApiResponse<T> {
  success:  boolean;
  data?:    T;
  error?:   ApiError;
  meta?:    PageMeta;
}

export interface ApiError {
  code:      string;
  message:   string;
  details?:  FieldError[];
}

export interface FieldError {
  field:   string;
  message: string;
}

export interface PageMeta {
  page:          number;
  pageSize:      number;
  totalElements: number;
  totalPages:    number;
}

export interface Page<T> {
  content:       T[];
  meta:          PageMeta;
}