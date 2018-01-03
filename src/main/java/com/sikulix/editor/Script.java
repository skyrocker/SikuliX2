package com.sikulix.editor;

import com.sikulix.api.Do;
import com.sikulix.api.Element;
import com.sikulix.api.Picture;
import com.sikulix.core.SX;
import com.sikulix.core.SXLog;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.*;
import java.util.List;

public class Script extends JPanel implements TableModelListener, ListSelectionListener {

  protected static final SXLog log = SX.getSXLog("SX.SCRIPTEDITOR");

  private Font customFont = new Font("Helvetica Bold", Font.PLAIN, 18);
  public Rectangle rectTable = null;
  private JFrame window;
  PopUpMenus popUpMenus = null;

  private JTable table = null;

  protected JTable getTable() {
    return table;
  }

  List<List<Cell>> data = new ArrayList<>();

  protected List<List<Cell>> getData() {
    return data;
  }

  private enum CellType {COMMAND, IMAGE, SCRIPT, VARIABLE, LIST, MAP, IMAGELIST, IMAGEMAP, TEXT}

  protected class Cell {
    private String value = "";
    private CellType cellType = CellType.TEXT;
    private int row = -1;
    private int col = -1;
    private int indentLevel = 0;

    protected Cell() {
      value = "";
    }

    protected Cell(String value) {
      this.value = value.trim();
    }

    protected Cell(String value, int col) {
      this.value = value.trim();
    }

    protected String getIndentMarker() {
      String indent = "";
      if (indentLevel == 1) {
        indent = ">";
      } else if (indentLevel == 2) {
        indent = ">>";
      } else if (indentLevel > 2) {
        indent = ">>!";
      }
      return indent;
    }

    protected void setIndent(int level) {
      if (level > -1) {
        indentLevel = level;
      }
    }

    protected int getIndent() {
      return indentLevel;
    }

    void doIndent() {
      indentLevel++;
    }

    void doDedent() {
      indentLevel = Math.max(0, --indentLevel);
      if (isBlock() && indentLevel == 0) {
        indentLevel = 1;
      }
    }

    private boolean block = false;

    void setBlock() {
      block = true;
    }

    boolean isBlock() {
      return block;
    }

    protected Cell asCommand(int row, int col) {
      if (!value.startsWith("#")) {
        value = "#" + value;
      }
      cellType = CellType.COMMAND;
      return this;
    }

    protected boolean isCommand() {
      return CellType.COMMAND.equals(cellType);
    }

    protected Cell asImage() {
      if (isEmpty() || "@".equals(value)) {
        value = "@?";
        imagename = "";
      } else if (!value.startsWith("@")) {
        imagename = value;
        value = "@?" + value;
      } else {
        imagename = value.replace("@", "").replace("?", "").trim();
      }
      cellType = CellType.IMAGE;
      return this;
    }

    protected boolean isImage() {
      return CellType.IMAGE.equals(cellType);
    }

    String imagename = "";
    Picture picture = null;

    private void getImage() {
      imagename = value.replace("@", "").replace("?", "");
      imagename = Do.input("Image Capture", "... enter a name", imagename);
      if (SX.isSet(imagename)) {
        new Thread(new Runnable() {
          @Override
          public void run() {
            window.setVisible(false);
            SX.pause(1);
            picture = Do.userCapture();
            if (SX.isNotNull(picture)) {
              picture.save(imagename, fScript.getParent());
            } else {
              imagename = "?" + imagename;
            }
            value = "@" + imagename;
            table.setValueAt(value, row, col);
            window.setVisible(true);
          }
        }).start();
      }
    }

    protected void capture() {
      asImage().getImage();
    }

    protected Element getCellClick() {
      return Script.getCellClick(row, col + 1, window, table);
    }

    protected void select() {
      new Thread(new Runnable() {
        @Override
        public void run() {
          Do.on().clickFast(getCellClick());
        }
      }).start();
    }

    protected void show() {
      asImage();
      if (isValid()) {
        loadPicture();
        if (SX.isNotNull(picture)) {
          new Thread(new Runnable() {
            @Override
            public void run() {
              picture.show(1);
              Do.on().clickFast(getCellClick());
            }
          }).start();
        } else {
          value = "@?" + imagename;
          table.setValueAt(value, row, col);
        }
      }
    }

    protected void find() {
      asImage();
      if (isValid()) {
        loadPicture();
        if (SX.isNotNull(picture)) {
          new Thread(new Runnable() {
            @Override
            public void run() {
              window.setVisible(false);
              Do.find(picture);
              Do.on().showMatch();
              window.setVisible(true);
            }
          }).start();
        } else {
          value = "@?" + imagename;
          table.setValueAt(value, row, col);
        }
      }
    }

    private void loadPicture() {
      if (SX.isNull(picture)) {
        File fPicture = new File(fScript.getParentFile(), imagename + ".png");
        if (fPicture.exists()) {
          picture = new Picture(fPicture.getAbsolutePath());
        }
      }
    }

    protected Cell asScript() {
      if (!value.startsWith("{")) {
        value = "{" + value;
      }
      cellType = CellType.SCRIPT;
      return this;
    }

    protected boolean isScript() {
      return CellType.SCRIPT.equals(cellType);
    }

    protected Cell asVariable() {
      if (!value.startsWith("=")) {
        value = "=" + value;
      }
      cellType = CellType.VARIABLE;
      return this;
    }

    protected boolean isVariable() {
      return CellType.VARIABLE.equals(cellType);
    }

    protected String eval(int row, int col) {
      return "";
    }

    protected boolean isEmpty() {
      return SX.isNotSet(value);
    }

    protected boolean isLineEmpty() {
      for (Cell cell : data.get(row)) {
        if (!cell.isEmpty()) {
          return false;
        }
      }
      return true;
    }

    protected boolean isValid() {
      boolean valid = SX.isSet(value);
      if (valid && isImage()) {
        valid &= !value.contains("?");
      }
      return valid;
    }

    protected String get() {
      return value;
    }

    protected Cell set(int row, int col) {
      this.row = row;
      this.col = col;
      return this;
    }

    protected Cell set(String value) {
      this.value = value;
      return this;
    }

    protected List<Cell> setLine(String... items) {
      List<Cell> oldLine = new ArrayList<>();
      for (Cell cell : data.get(row)) {
        oldLine.add(cell);
      }
      if (items.length == 0) {
        data.set(row, new ArrayList<>());
      } else {
        int col = 1;
        for (String item : items) {
          cellAt(row, col++).set(item);
        }
      }
      return oldLine;
    }
  }

  boolean shouldTrace = false;

  public Script(JFrame frame, String[] args) {
    super(new GridLayout(1, 0));

    if ("trace".equals(args[0])) {
      log.on(SXLog.TRACE);
      shouldTrace = true;
    }

    this.window = frame;

    loadScript();

    table = new ScriptTable(new MyTableModel());

    table.setAutoCreateColumnsFromModel(false);

    FontMetrics metrics = table.getFontMetrics(customFont);
    table.setRowHeight(metrics.getHeight() + 4); // set row height to match font

    table.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
    table.setCellSelectionEnabled(true);

    Rectangle monitor = SX.getSXLOCALDEVICE().getMonitor();
    Dimension tableDim = new Dimension((int) (monitor.width * 0.8), (int) (monitor.height * 0.8));
    rectTable = new Rectangle();
    rectTable.setSize(tableDim);
    rectTable.setLocation(tableDim.width / 8, tableDim.height / 9);

    table.setTableHeader(new JTableHeader(table.getColumnModel()) {
      @Override
      public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.height = 30;
        return d;
      }
    });

    table.getTableHeader().setBackground(new Color(230, 230, 230));

    int tableW = tableDim.width;
    int tableCols = table.getColumnCount();
    int col0W = 70;
    int col1W = 90;
    int colLast = 400;
    table.getColumnModel().getColumn(0).setPreferredWidth(col0W);
    table.getColumnModel().getColumn(1).setPreferredWidth(col1W);
    table.getColumnModel().getColumn(maxCol).setPreferredWidth(colLast);
    int colsW = (tableW - col0W - col1W - colLast) / (tableCols - 3);
    for (int n = 2; n < tableCols - 1; n++) {
      table.getColumnModel().getColumn(n).setPreferredWidth(colsW);
    }

    table.setGridColor(Color.LIGHT_GRAY);
    table.setShowGrid(false);
    table.setShowHorizontalLines(true);

    table.setPreferredScrollableViewportSize(tableDim);
    table.setFillsViewportHeight(true);

    table.getModel().addTableModelListener(this);

    ListSelectionModel listSelectionModel = table.getSelectionModel();
    listSelectionModel.addListSelectionListener(this);
    table.setSelectionModel(listSelectionModel);

    table.addMouseListener(new MyMouseAdapter(1));
    table.getTableHeader().addMouseListener(new MyMouseAdapter(0));

    JScrollPane scrollPane = new JScrollPane(table);
    add(scrollPane);
    popUpMenus = new PopUpMenus(this);
  }

  @Override
  public void tableChanged(TableModelEvent e) {
    int firstRow = e.getFirstRow();
    int lastRow = e.getLastRow();
    int column = e.getColumn();
    String changed = "NO_CELL";
    if (firstRow == lastRow) {
      changed = data.get(firstRow).get(column - 1).get();
    }
    if (column > 0) {
      log.trace("changed: [%d, %d] %s", firstRow, column - 1, changed);
    }
  }

  @Override
  public void valueChanged(ListSelectionEvent e) {
    if (!e.getValueIsAdjusting()) {
      if (!e.toString().contains("={}")) {
        int[] rows = table.getSelectedRows();
        int[] cols = table.getSelectedColumns();
        if (rows.length > 0 && cols.length > 0) {
          //log.trace("selected: (%d, %d)", rows[0], cols[0]);
        }
      }
    }
  }

  int maxCol = 7;

  class MyTableModel extends AbstractTableModel {

    public int getColumnCount() {
      return maxCol + 1;
    }

    public int getRowCount() {
      if (data.size() == 0) {
        data.add(new ArrayList<>());
      }
      return data.size();
    }

    public String getColumnName(int col) {
      if (col == 0) return "    Line";
      if (col == 1) return "Command";
      return String.format("Item%d", col - 1);
    }

    public Object getValueAt(int row, int col) {
      if (col == 0) {
        return String.format("%6d %s", row + 1, cellAt(row, 2).getIndentMarker());
      }
      if (row > data.size() - 1) {
        return "";
      }
      int lineCol = col - 1;
      List<Cell> line = data.get(row);
      if (lineCol > line.size() - 1) {
        return "";
      }
      return data.get(row).get(lineCol).get();
    }

    public Class getColumnClass(int c) {
      return String.class;
    }

    public boolean isCellEditable(int row, int col) {
      return true;
    }

    public void setValueAt(Object value, int row, int col) {
      if (row < 0 && col < 0) {
        fireTableDataChanged();
        return;
      }
      String given = ((String) value).trim();
      if (col == 0) {
        return;
      }
      if (col == 1) {
        if (!given.startsWith("#")) {
          given = "#" + given;
        }
        String cmd = given.trim();
        String parm = "";
        int ix = cmd.indexOf(" ");
        if (ix > -1) {
          parm = cmd.substring(ix);
          cmd = cmd.substring(0, ix);
        }
        String fromShort = commandShort.get(cmd);
        if (SX.isNotNull(fromShort)) {
          cmd = fromShort;
        } else if (SX.isNull(commandTemplates.get(cmd))) {
          given = given + "?";
        }
        if (!given.endsWith("?")) {
          addCommandTemplate(cmd, row, col);
        } else {
          cmd = given;
          parm = "";
        }
        cellAt(row, col).select();
      } else {
        cellAt(row, col).set(given);
        fireTableCellUpdated(row, col);
      }
    }
  }

  Point currentCell = null;
  String savedCell = "";

  private class ScriptTable extends JTable {

    public ScriptTable(AbstractTableModel tableModel) {
      super(tableModel);
      init();
    }

    private void init() {
      commandShort.put("#", "");
      commandShort.put("#c", "#click");
      commandShort.put("#cr", "#clickRight");
      commandShort.put("#cd", "#clickDouble");
      commandShort.put("#f", "#find");
      commandShort.put("#fa", "#findAll");
      commandShort.put("#fb", "#findBest");
      commandShort.put("#fy", "#findAny");
      commandShort.put("#v", "#vanish");
      commandShort.put("#w", "#wait");
      commandShort.put("#l", "#loop");
      commandShort.put("#lf", "#loopFor");
      commandShort.put("#lw", "#loopWith");
      commandShort.put("#i", "#if");
      commandShort.put("#in", "#ifNot");
      commandShort.put("#e", "#else");
      commandShort.put("#ei", "#elif");
      commandShort.put("#ein", "#elifNot");
      commandShort.put("#p", "#print");
      commandShort.put("#pf", "#printf");

      commandTemplates.put("#find", new String[]{"", "@?", "result"});
      commandTemplates.put("#wait", new String[]{"", "@?", "result"});
      commandTemplates.put("#vanish", new String[]{"", "@?", "result"});
      commandTemplates.put("#findAll", new String[]{"", "@?", "result"});
      commandTemplates.put("#findBest", new String[]{"", "@[?", "result"});
      commandTemplates.put("#findAny", new String[]{"", "@[?", "result"});
      commandTemplates.put("#click", new String[]{"", "@?", "result"});
      commandTemplates.put("#clickRight", new String[]{"", "@?", "result"});
      commandTemplates.put("#clickDouble", new String[]{"", "@?", "result"});
      commandTemplates.put("#if", new String[]{"", "condition", "block"});
      commandTemplates.put("#ifNot", new String[]{"", "condition", "block"});
      commandTemplates.put("#else", new String[]{"", "block"});
      commandTemplates.put("#elif", new String[]{"", "condition", "block"});
      commandTemplates.put("#elifNot", new String[]{"", "condition", "block"});
      commandTemplates.put("#loop", new String[]{"", "condition", "block"});
      commandTemplates.put("#loopWith", new String[]{"", "listvariable", "block"});
      commandTemplates.put("#loopFor", new String[]{"", "count step from", "block"});
      commandTemplates.put("#print", new String[]{""});
      commandTemplates.put("#printf", new String[]{"", "template", "variable..."});
      commandTemplates.put("#log", new String[]{"", "template", "variable..."});
      commandTemplates.put("#pop", new String[]{"", "message", "result"});
    }

    @Override
    public boolean editCellAt(int row, int col, EventObject e) {
      currentCell = null;
      int nextSelection = 0;
      if (e instanceof KeyEvent) {
        int keyCode = ((KeyEvent) e).getExtendedKeyCode();
        if (keyCode == 0 || keyCode == KeyEvent.VK_ESCAPE || keyCode == KeyEvent.VK_META) {
          return false;
        }
        if (col == 0) {
          if (keyCode == KeyEvent.VK_PLUS) {
            data.add(row + 1, new ArrayList<>());
            tableHasChanged();
            table.setRowSelectionInterval(row + 1, row + 1);
            table.setColumnSelectionInterval(col + 1, col + 1);
            new Thread(new Runnable() {
              @Override
              public void run() {
                Do.write("#ESC.");
              }
            }).start();
            return false;
          }
          if (keyCode == KeyEvent.VK_BACK_SPACE) {
            PopUpMenus.savedLine = cellAt(row, col + 1).setLine();
            tableHasChanged();
            table.setRowSelectionInterval(row, row);
            table.setColumnSelectionInterval(col, col);
            return false;
          }
          if (keyCode == KeyEvent.VK_GREATER) {
            indent(row, col);
            return false;
          }
          if (keyCode == KeyEvent.VK_LESS) {
            dedent(row, col);
            return false;
          }
        }
        if (col == 1 && keyCode == KeyEvent.VK_SPACE && cellAt(row, col).isEmpty()) {
          Rectangle cellRect = table.getCellRect(row, col, false);
          PopUpMenus.Command.pop(table, cellRect.x, cellRect.y);
          return false;
        } else if (keyCode == KeyEvent.VK_SPACE) {
          log.trace("EditBox: should open");
          Cell cell = cellAt(row, Math.max(1, col));
          Map<String, Object> editorContent = new HashMap<>();
          new Thread(new Runnable() {
            @Override
            public void run() {
              JFrame frame = new JFrame("EditCell");
              JPanel cp = new JPanel(new BorderLayout());
              RSyntaxTextArea textArea = new RSyntaxTextArea(20, 60);
              textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
              textArea.setCodeFoldingEnabled(true);
              textArea.setText(cell.get());
              RTextScrollPane sp = new RTextScrollPane(textArea);
              cp.add(sp);
              frame.setContentPane(cp);
              frame.pack();
              frame.setLocationRelativeTo(null);
              frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
              frame.setVisible(true);
              while(frame.isVisible()) {
                SX.pause(0.3);
              }
              cell.set(textArea.getText());
              frame.dispose();
            }
          }).start();
          return false;
        } else if (keyCode == KeyEvent.VK_BACK_SPACE && cellAt(row, col).isEmpty()) {
          getModel().setValueAt(savedCell, row, col);
          return false;
        } else if (keyCode == KeyEvent.VK_F1) {
          log.trace("(%d,%d): F1: help: %s", row, col, table.getValueAt(row, col));
          return false;
        } else if (keyCode == KeyEvent.VK_F2) {
          log.trace("Colx: F2: save script");
          saveScript();
          return false;
        } else if (keyCode == KeyEvent.VK_F3) {
          log.trace("Colx: F3: open script");
          loadScript();
          table.getModel().setValueAt("", -1, -1);
          return false;
        } else if (keyCode == KeyEvent.VK_F4) {
          log.trace("Colx: F4: run script");
          if (col == 0) {
            runScript(0, data.size() - 1);
          } else {
            runScript(row, row);
          }
          return false;
        } else if (keyCode == KeyEvent.VK_F5) {
          log.trace("(%d,%d): F5: capture: %s", row, col, cellAt(row, col).get());
          cellAt(row, col).capture();
          return false;
        } else if (keyCode == KeyEvent.VK_F6) {
          log.trace("(%d,%d): F6: show: %s", row, col, cellAt(row, col).get());
          cellAt(row, col).show();
          return false;
        } else if (keyCode == KeyEvent.VK_F7) {
          log.trace("(%d,%d): F7: find: %s", row, col, cellAt(row, col).get());
          cellAt(row, col).find();
          return false;
        } else if (keyCode == KeyEvent.VK_DELETE || keyCode == KeyEvent.VK_BACK_SPACE) {
          log.trace("(%d,%d): DELETE: make cell empty", row, col);
          savedCell = (String) getModel().getValueAt(row, col);
          getModel().setValueAt("", row, col);
          return false;
        }
        log.trace("keycode: %d %s", keyCode, KeyEvent.getKeyText(keyCode));
      }
      if (col > 0)

      {
        return super.editCellAt(row, col, e);
      }
      return false;
    }
  }

  protected Cell cellAt(int row, int col) {
    int lineCol = col - 1;
    if (row < 0) {
      row = 0;
    }
    if (row > data.size() - 1) {
      data.add(new ArrayList<>());
    }
    List<Cell> line = data.get(row);
    if (lineCol > line.size() - 1) {
      for (int n = line.size(); n <= lineCol; n++) {
        line.add(new Cell());
      }
    }
    return data.get(row).get(lineCol).set(row, col);
  }

  protected static Element getCellClick(int row, int col, JFrame window, JTable table) {
    Point windowLocation = window.getLocation();
    Rectangle cell = table.getCellRect(row, col, false);
    windowLocation.x += cell.x + 10;
    windowLocation.y += cell.y + 70;
    return new Element(windowLocation);
  }

  private class MyMouseAdapter extends MouseAdapter {

    private int type = 1;

    private MyMouseAdapter() {
    }

    public MyMouseAdapter(int type) {
      this.type = type;
    }

    public void mouseReleased(MouseEvent me) {
      Point where = me.getPoint();
      int row = table.rowAtPoint(where);
      int col = table.columnAtPoint(where);
      int button = me.getButton();

      if (row < 0) {
        return;
      }
      if (type > 0) {
        log.trace("clicked: R%d C%d B%d", row, col, button);
      } else {
        log.trace("clicked: Header C%d B%d", col, button);
      }
      if (type > 0 && button > 1) {
        setSelection(row, col);
      }
      if (button == 3) {
        if (type > 0) {
          if (col == 1) {
            PopUpMenus.Command.pop(table, where.x, where.y);
          } else if (col == 0) {
            PopUpMenus.Action.pop(table, where.x, where.y);
          } else {
            PopUpMenus.Default.pop(table, where.x, where.y);
          }
        } else {

        }
        return;
      }
    }

  }

  protected void tableHasChanged() {
    getTable().getModel().setValueAt(null, -1, -1);
  }

  //TODO must focus with a click???
  protected void tableHasChanged(int row, int col) {
    Element cellClick = getCellClick(row, 0, window, table);
    getTable().getModel().setValueAt(null, -1, -1);
    new Thread(new Runnable() {
      @Override
      public void run() {
        Do.on().clickFast(cellClick);
        Do.on().clickFast(cellClick);
      }
    }).start();
  }

  private void setSelection(int row, int col) {
    table.setRowSelectionInterval(row, row);
    table.setColumnSelectionInterval(col, col);
  }

  void indent(int row, int col) {
    log.trace("action: indent");
    cellAt(row, 2).doIndent();
    tableHasChanged(row, 0);
  }

  void dedent(int row, int col) {
    log.trace("action: dedent");
    cellAt(row, 2).doDedent();
    tableHasChanged(row, 0);
  }

  private File fScript = new File(SX.getSXSTORE(), "scripteditor/script.txt");

  protected void loadScript() {
    data.clear();
    resultsCounter = 0;
    String theScript = com.sikulix.core.Content.readFileToString(fScript);
    for (String line : theScript.split("\\n")) {
      List<Cell> aLine = new ArrayList<>();
      int colCount = 1;
      for (String cellText : line.split("\\t")) {
        if (cellText.contains("=result")) {
          int count = -1;
          String resultCount = cellText.replace("=result", "").trim();
          try {
            count = Integer.parseInt(resultCount);
          } catch (Exception ex) {
            cellText += "?";
          }
          if (count >= resultsCounter) {
            resultsCounter = count + 1;
          }
        }
        aLine.add(new Cell(cellText));
        if (++colCount > maxCol) {
          break;
        }
      }
      if (aLine.size() > 0) {
        data.add(aLine);
      }
    }
  }

  protected void saveScript() {
    String theScript = "";
    for (List<Cell> line : data) {
      String sLine = "";
      String sTab = "";
      for (Cell cell : line) {
        sLine += sTab + cell.get();
        sTab = "\t";
      }
      if (SX.isSet(sLine)) {
        theScript += sLine + "\n";
      }
    }
    if (SX.isSet(theScript)) {
      fScript.getParentFile().mkdirs();
      com.sikulix.core.Content.writeStringToFile(theScript, fScript);
    }
  }

  int resultsCounter = 0;

  List<String> variables = new ArrayList<>();

  Map<String, String> images = new HashMap<>();

  Map<String, String> commandShort = new HashMap<>();

  Map<String, String[]> commandTemplates = new HashMap<>();

  protected boolean addCommandTemplate(String command, int row, int col) {
    if (cellAt(row, col).isLineEmpty()) {
      String[] template = getCommandTemplate(command);
      boolean shouldIndent = false;
      if (template[0].startsWith(">")) {
        shouldIndent = true;
        template[0] = template[0].substring(1);
      }
      cellAt(row, col).setLine(template);
      if (shouldIndent) {
        cellAt(row, 2).setIndent(cellAt(row - 1, 2).getIndent() + 1);
        cellAt(row, 2).setBlock();
      } else if (cellAt(row - 1, 2).getIndent() > 0) {
        cellAt(row, 2).setIndent(cellAt(row - 1, 2).getIndent());
      }
      tableHasChanged();
      return true;
    }
    return false;
  }

  String[] getCommandTemplate(String command) {
    String[] commandLine = commandTemplates.get(command).clone();
    if (SX.isNull(commandLine)) {
      commandLine = new String[]{"#error!"};
    } else {
      commandLine[0] = commandLine[0] + command;
      if ("result".equals(commandLine[commandLine.length - 1])) {
        commandLine[commandLine.length - 1] = "=R" + resultsCounter++;
      } else if ("block".equals(commandLine[commandLine.length - 1])) {
        commandLine[0] = ">" + command;
      }
    }
    return commandLine;
  }

  protected void runScript(int lineFrom, int lineTo) {
    window.setVisible(false);
    for (int n = lineFrom; n <= lineTo; n++) {
      String sLine = "";
      String sep = "";
      for (Cell cell : data.get(n)) {
        sLine += sep + cell.get().trim();
        sep = " | ";
      }
      log.trace("runscript: (%4d) %s", n, sLine);
    }
    //SX.pause(2);
    window.setVisible(true);
  }
}


