package dan200.computercraft.api.turtle;

public final class TurtleCommandResult {
   private static final TurtleCommandResult s_success = new TurtleCommandResult(true, null, null);
   private static final TurtleCommandResult s_emptyFailure = new TurtleCommandResult(false, null, null);
   private final boolean m_success;
   private final String m_errorMessage;
   private final Object[] m_results;

   public static TurtleCommandResult success() {
      return success(null);
   }

   public static TurtleCommandResult success(Object[] results) {
      return results != null && results.length != 0 ? new TurtleCommandResult(true, null, results) : s_success;
   }

   public static TurtleCommandResult failure() {
      return failure(null);
   }

   public static TurtleCommandResult failure(String errorMessage) {
      return errorMessage == null ? s_emptyFailure : new TurtleCommandResult(false, errorMessage, null);
   }

   private TurtleCommandResult(boolean success, String errorMessage, Object[] results) {
      this.m_success = success;
      this.m_errorMessage = errorMessage;
      this.m_results = results;
   }

   public boolean isSuccess() {
      return this.m_success;
   }

   public String getErrorMessage() {
      return this.m_errorMessage;
   }

   public Object[] getResults() {
      return this.m_results;
   }
}
