package com.softwire.todos;

import java.io.File;

class CodeTodo {
    private final File file;
    private final int lineNumber;
    private final String line;
    private final GitCheckout containingGitCheckout;

    @java.beans.ConstructorProperties({"file", "lineNumber", "line"})
    public CodeTodo(File file, int lineNumber, String line, GitCheckout containingGitCheckout) {
        this.file = file;
        this.lineNumber = lineNumber;
        this.line = line;
        this.containingGitCheckout = containingGitCheckout;
    }

    public File getFile() {
        return this.file;
    }

    public int getLineNumber() {
        return this.lineNumber;
    }

    public String getLine() {
        return this.line;
    }

    public GitCheckout getContainingGitCheckout() {
        return containingGitCheckout;
    }

    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof CodeTodo)) return false;
        final CodeTodo other = (CodeTodo) o;
        final Object this$file = this.getFile();
        final Object other$file = other.getFile();
        if (this$file == null ? other$file != null : !this$file.equals(other$file)) return false;
        if (this.getLineNumber() != other.getLineNumber()) return false;
        final Object this$line = this.getLine();
        final Object other$line = other.getLine();
        if (this$line == null ? other$line != null : !this$line.equals(other$line)) return false;
        return true;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $file = this.getFile();
        result = result * PRIME + ($file == null ? 43 : $file.hashCode());
        result = result * PRIME + this.getLineNumber();
        final Object $line = this.getLine();
        result = result * PRIME + ($line == null ? 43 : $line.hashCode());
        return result;
    }

    public String toString() {
        return "com.softwire.todos.CodeTodo(file=" + this.getFile() + ", lineNumber=" + this.getLineNumber() + ", line=" + this.getLine() + ")";
    }
}
