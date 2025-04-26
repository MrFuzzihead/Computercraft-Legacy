package dan200.computercraft.core.computer;

import java.util.LinkedList;

public class MainThread {
   private static final int MAX_TASKS_PER_TICK = 1000;
   private static final int MAX_TASKS_TOTAL = 50000;
   private static final LinkedList<ITask> m_outstandingTasks = new LinkedList<>();
   private static final Object m_nextUnusedTaskIDLock = new Object();
   private static long m_nextUnusedTaskID = 0L;

   public static long getUniqueTaskID() {
      synchronized (m_nextUnusedTaskIDLock) {
         return ++m_nextUnusedTaskID;
      }
   }

   public static boolean queueTask(ITask task) {
      synchronized (m_outstandingTasks) {
         if (m_outstandingTasks.size() < MAX_TASKS_TOTAL) {
            m_outstandingTasks.addLast(task);
            return true;
         } else {
            return false;
         }
      }
   }

   public static void executePendingTasks() {
      for (int tasksThisTick = 0; tasksThisTick < MAX_TASKS_PER_TICK; tasksThisTick++) {
         ITask task = null;
         synchronized (m_outstandingTasks) {
            if (m_outstandingTasks.size() > 0) {
               task = m_outstandingTasks.removeFirst();
            }
         }

         if (task == null) {
            break;
         }

         task.execute();
      }
   }
}
