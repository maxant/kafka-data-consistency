package ch.maxant.kdc.web;

import java.util.List;

public class KsqlResponseEntity {

    private Row row;
    private String errorMessage;
    private String finalMessage;
    private boolean terminal;

    public Row getRow() {
        return row;
    }

    public void setRow(Row row) {
        this.row = row;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getFinalMessage() {
        return finalMessage;
    }

    public void setFinalMessage(String finalMessage) {
        this.finalMessage = finalMessage;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public void setTerminal(boolean terminal) {
        this.terminal = terminal;
    }

    public static class Row {

        private List<Object> columns;

        public List<Object> getColumns() {
            return columns;
        }

        public void setColumns(List<Object> columns) {
            this.columns = columns;
        }
    }
}
