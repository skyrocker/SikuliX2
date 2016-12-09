/*
 * Copyright (c) 2016 - sikulix.com - MIT license
 */

package com.sikulix.scripting;

import com.sikulix.api.*;
import com.sikulix.core.SX;
import com.sikulix.core.SXLog;

import java.awt.*;
import java.lang.reflect.Method;

public class JavaScriptHelper {

  private static SXLog log = SX.getLogger("SX.JavaScriptHelper");
  private static int lvl = SXLog.DEBUG;

  static Element scr, scrSaved;

  /**
   *
   * @return true if we are on Java 8+
   */
  public static boolean isNashorn() {
    return SX.isJava8();
  }

  /**
   * INTERNAL USE: call interface for JavaScript to be used with predefined functions
   * @param function the function's name
   * @param args the parameters
   * @return the object returned by the function or null
   */
  public static Object call(String function, Object... args) {
    Method m = null;
    Object retVal = null;
    int count = 0;
    for (Object aObj : args) {
      if (aObj == null || aObj.getClass().getName().endsWith("Undefined")) {
        break;
      }
      if (aObj instanceof String && ((String) aObj).contains("undefined")) {
        break;
      }
      count++;
    }
    Object[] newArgs = new Object[count];
    for(int n = 0; n < count; n++) {
      newArgs[n] = args[n];
    }
    try {
      m = Commands.class.getMethod(function, Object[].class);
      retVal = m.invoke(null, (Object) newArgs);
    } catch (Exception ex) {
      m = null;
    }
    return retVal;
  }

  //<editor-fold defaultstate="collapsed" desc="conversions">
  private static boolean isNumber(Object aObj) {
    if (aObj instanceof Integer || aObj instanceof Long || aObj instanceof Float || aObj instanceof Double) {
      return true;
    }
    return false;
  }

  private static int getInteger(Object aObj, int deflt) {
    Integer val = deflt;
    if (aObj instanceof Integer || aObj instanceof Long) {
      val = (Integer) aObj;
    }
    if (aObj instanceof Float) {
      val = Math.round((Float) aObj);
    }
    if (aObj instanceof Double) {
      val = (int) Math.round((Double) aObj);
    }
    return val;
  }

  private static int getInteger(Object aObj) {
    return getInteger(aObj, 0);
  }

  private static double getNumber(Object aObj, Double deflt) {
    Double val = deflt;
    if (aObj instanceof Integer) {
      val = 0.0 + (Integer) aObj;
    } else if (aObj instanceof Long) {
      val = 0.0 + (Long) aObj;
    } else if (aObj instanceof Float) {
      val = 0.0 + (Float) aObj;
    } else if (aObj instanceof Double) {
      val = (Double) aObj;
    }
    return val;
  }

  private static double getNumber(Object aObj) {
    return getNumber(aObj, 0.0);
  }
//</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="use/use1">

  /**
   * all following undotted function calls will use the given screen or region
   * until this is changed by a later use()<br>
   * -- no args: use Screen(0) (this is the default after start)<br>
   * -- a number: use Screen(number), Screen(0) if not valid<br>
   * -- a region: use the given region<br>
   * @param args
   * @return the used region
   */
  public static Element use(Object... args) {
    logCmd("use", args);
    scrSaved = null;
    return usex(args);
  }

  /**
   * same as use(), but only affects the next processed undotted function
   * after that the use() active before is restored
   * @param args see use()
   * @return the used region
   */
  public static Element use1(Object... args) {
    logCmd("use1", args);
    scrSaved = scr;
    return usex(args);
  }

  /**
   * INTERNAL USE: restore a saved use() after a use1()
   */
  public static void restoreUsed() {
    if (scrSaved != null) {
      scr = scrSaved;
      scrSaved = null;
      logCmd("restoreUsed", "restored: %s", scr);
    }
  }

  private static Element usex(Object... args) {
    int len = args.length;
    int nScreen = -1;
    if (len == 0 || len > 1) {
      scr = new Element(0);
    } else {
      nScreen = getInteger(args[0], -1);
      if (nScreen > -1) {
        scr = new Element(nScreen);
      } else {
        Object oReg = args[0];
        if (oReg instanceof Element) {
          scr = (Element) oReg;
        }
      }
    }
    return scr;
  }
//</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="wait/waitVanish/exists">

  /**
   * wait for the given visual to appear within the given wait time<br>
   * args [String|Pattern|Double, [Double, [Float]]] (max 3 args)<br>
   * arg1: String/Pattern to search or double time to wait (rest ignored)<br>
   * arg2: time to wait in seconds<br>
   * arg3: minimum similarity to use for search (overwrites Pattern setting)<br>
   * @param args
   * @return the match or throws FindFailed
   * @throws FindFailed
   */
  public static Match wait(Object... args) throws FindFailed {
    logCmd("wait", args);
    Object[] realArgs = waitArgs(args);
    return waitx((String) realArgs[0], (Pattern) realArgs[1], (Double) realArgs[2], (Float) realArgs[3]);
  }

  private static Match waitx(String image, Pattern pimage, double timeout, float score) throws FindFailed {
    Object aPattern = null;
    if (image != null) {
      if (score > 0) {
        aPattern = new Pattern(image).similar(score);
      } else {
        aPattern = new Pattern(image);
      }
    } else if (pimage != null) {
      aPattern = pimage;
    }
    if (aPattern != null) {
      if (timeout > -1.0) {
        return scr.wait((Pattern) aPattern, timeout);
      }
      return scr.wait((Pattern) aPattern);
    }
    return null;
  }

  private static Object[] waitArgs(Object... args) {
    int len = args.length;
    String image = "";
    float score = 0.0f;
    double timeout = -1.0f;
    boolean argsOK = true;
    Object[] realArgs = new Object[] {null, null, (Double) (-1.0), (Float) 0f};
    if (len == 0 || len > 3) {
      argsOK = false;
    } else {
      Object aObj = args[0];
      if (aObj == null) {
        return realArgs;
      }
      if (isJSON(aObj)) {
        aObj = fromJSON(aObj);
      }
      if (aObj instanceof String) {
        realArgs[0] = aObj;
      } else if (aObj instanceof Pattern) {
        realArgs[1] = aObj;
        if (len > 1 && isNumber(args[1])) {
          realArgs[2] = (Double) getNumber(args[1]);
        }
      } else if (isNumber(aObj)) {
        scr.wait(getNumber(aObj));
        return null;
      } else {
        argsOK = false;
      }
    }
    if (argsOK && len > 1 && realArgs[1] == null) {
      if (len > 2 && isNumber(args[2])) {
        score = (float) getNumber(args[2]) / 100.0f;
        if (score < 0.7) {
          score = 0.7f;
        } else if (score > 0.99) {
          score = 0.99f;
        }
      }
      if (score > 0.0f) {
        realArgs[3] = (Float) score;
      }
      if (len > 1 && isNumber(args[1])) {
        realArgs[2] = (Double) getNumber(args[1]);
      }
    }
    if (!argsOK) {
      throw new UnsupportedOperationException(
              "Commands.wait: parameters: String/Pattern:image, float:timeout, int:score");
    }
    return realArgs;
  }

  /**
   * wait for the given visual to vanish within the given wait time
   * @param args see wait()
   * @return true if not there from beginning or vanished within wait time, false otherwise
   */
  public static boolean waitVanish(Object... args) {
    logCmd("waitVanish", args);
    Object aPattern;
    Object[] realArgs = waitArgs(args);
    String image = (String) realArgs[0];
    Pattern pimage = (Pattern) realArgs[1];
    double timeout = (Double) realArgs[2];
    float score = (Float) realArgs[3];
    if (image != null) {
      if (score > 0) {
        aPattern = new Pattern(image).similar(score);
      } else {
        aPattern = new Pattern(image);
      }
    } else {
      aPattern = pimage;
    }
    if (timeout > -1.0) {
      return scr.waitVanish((Pattern) aPattern, timeout);
    }
    return scr.waitVanish((Pattern) aPattern);
  }

  /**
   * wait for the given visual to appear within the given wait time
   * @param args see wait()
   * @return the match or null if not found within wait time (no FindFailed exception)
   */
  public static Match exists(Object... args) {
    logCmd("exists", args);
    Match match = null;
    Object[] realArgs = waitArgs(args);
    if ((Double) realArgs[2] < 0.0) {
      realArgs[2] = 0.0;
    }
    try {
      match = waitx((String) realArgs[0], (Pattern) realArgs[1], (Double) realArgs[2], (Float) realArgs[3]);
    } catch (Exception ex) {
      return null;
    }
    return match;
  }
//</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="hover/click/doubleClick/rightClick">

  /**
   * move the mouse to the given location with a given offset<br>
   * 3 parameter configurations:<br>
   * --1: wait for a visual and move mouse to it (args see wait())<br>
   * --2: move to the given region/location/match with a given offset<br>
   * --3: move to the given offset relative to the last match of the region in use
   * @param args
   * @return the evaluated location to where the mouse should have moved
   */
  public static Element hover(Object... args) {
    logCmd("hover", args);
    return hoverx(args);
  }

  private static Element hoverx(Object... args) {
    int len = args.length;
    Match aMatch;
    if (len == 0 || args[0] == null) {
      Mouse.get().move(scr.getMatchPoint());
      return new Element(); // Mouse.at();
    }
    if (len < 4) {
      Object aObj = args[0];
      Element loc = null;
      if (isJSON(aObj)) {
        aObj = fromJSON(aObj);
      }
      if (aObj instanceof String || aObj instanceof Pattern) {
        try {
          aMatch = wait(args);
          Mouse.get().move(aMatch.getTarget());
        } catch (Exception ex) {
          Mouse.get().move(scr.getMatchPoint());
        }
        return new Element(); // Mouse.at();
      } else if (aObj instanceof Element) {
        loc = new Element(); //(Region) aObj).getTarget();
      } else if (aObj instanceof Element) {
        loc = (Element) aObj;
      }
      if (len > 1) {
        if (isNumber(aObj) && isNumber(args[1])) {
          Element match = scr.getMatchPoint();
          match.translate(getInteger(aObj), getInteger(args[1]));
          Mouse.get().move(match);
          return new Element(); //Mouse.at();
        } else if (len == 3 && loc != null && isNumber(args[1]) && isNumber(args[2])) {
          //Mouse.move(loc.offset(getInteger(args[1], 0), getInteger(args[2], 0)));
          return new Element();  //Mouse.at();
        }
      }
      if (loc != null) {
        //Mouse.move(loc);
        return  new Element(); //Mouse.at();
      }
    }
    Mouse.get().move(scr.getMatchPoint());
    return new Element();  //Mouse.at();
  }

  /**
   * move the mouse with hover() and click using the left button
   * @param args see hover()
   * @return the Element, where the click was done
   */
  public static Element click(Object... args) {
    logCmd("click", args);
    hoverx(args);
    Mouse.get().click(null, "L");
    return  new Element(); // Mouse.at();
  }

  /**
   * move the mouse with hover() and double click using the left button
   * @param args see hover()
   * @return the Element, where the double click was done
   */
  public static Element doubleClick(Object... args) {
    logCmd("doubleClick", args);
    hoverx(args);
    Mouse.get().click(null, "LD");
    return  new Element(); //Mouse.at();
  }

  /**
   * move the mouse with hover() and do a right click
   * @param args see hover()
   * @return the Element, where the right click was done
   */
  public static Element rightClick(Object... args) {
    logCmd("rightClick", args);
    hoverx(args);
    Mouse.get().click(null, "R");
    return  new Element(); //Mouse.at();
  }
//</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="type/write/paste">

  /**
   * just doing a currentRegion.paste(text) (see paste())
   * @param args only one parameter being a String
   * @return false if problems with paste(), true otherwise
   */

  public static boolean paste(Object... args) {
    logCmd("paste", args);
    Object[] realArgs = typeArgs(args);
    return scr.paste((String) realArgs[0]);
  }

  /**
   * just doing a currentRegion.write(text) (see write())
   * @param args only one parameter being a String
   * @return false if problems with write(), true otherwise
   */
  public static boolean write(Object... args) {
    logCmd("write", args);
    Object[] realArgs = typeArgs(args);
    return scr.write((String) realArgs[0]);
  }

  private static Object[] typeArgs(Object... args) {
    Object[] realArgs = new Object[] {null};
    if (! (args[0] instanceof String)) {
      throw new UnsupportedOperationException("Commands.type/paste/write: parameters: String:text");
    }
    realArgs[0] = args[0];
    return realArgs;
  }
//</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="JSON support">

  /**
   * check wether the given object is in JSON format as ["ID", ...]
   * @param aObj
   * @return true if object is in JSON format, false otherwise
   */
  public static boolean isJSON(Object aObj) {
    if (aObj instanceof String) {
      return ((String) aObj).startsWith("[\"");
    }
    return false;
  }

  /**
   * experimental: create the real object from the given JSON<br>
   * take care: content length not checked if valid (risk for index out of bounds)<br>
   * planned to be used with a socket/RPC based interface to any framework (e.g. C#)
   * Region ["R", x, y, w, h]<br>
   * Location ["L", x, y]<br>
   * Match ["M", x, y, w, h, score, offx, offy]<br>
   * Screen ["S", x, y, w, h, id]<br>
   * Pattern ["P", "imagename", score, offx, offy]<br>
   * These real objects have a toJSON(), that returns these JSONs<br>
   * @param aObj
   * @return the real object or the given object if it is not one of these JSONs
   */
  public static Object fromJSON(Object aObj) {
    if (!isJSON(aObj)) {
      return aObj;
    }
    Object newObj = null;
    String[] json = ((String) aObj).split(",");
    String last = json[json.length-1];
    if (!last.endsWith("]")) {
      return aObj;
    } else {
      json[json.length-1] = last.substring(0, last.length()-1);
    }
    String oType = json[0].substring(2,3);
    if (!"SRML".contains(oType)) {
      return aObj;
    }
    if ("S".equals(oType)) {
      newObj = new Element(intFromJSON(json, 5));
    } else if ("R".equals(oType)) {
      newObj = new Element(rectFromJSON(json));
    } else if ("M".equals(oType)) {
      Match newMatch = new Match(rectFromJSON(json));
      newMatch.setScore(dblFromJSON(json, 5)/100);
      newMatch.setTarget(intFromJSON(json, 6), intFromJSON(json, 7));
    } else if ("L".equals(oType)) {
      newObj = null; //new Location(locFromJSON(json));
    } else if ("P".equals(oType)) {
      newObj = new Pattern(json[1]);
      ((Pattern) newObj).similar(fltFromJSON(json, 2));
      ((Pattern) newObj).setTarget(intFromJSON(json, 3), intFromJSON(json, 4));
    }
    return newObj;
  }

  private static Rectangle rectFromJSON(String[] json) {
    int[] vals = new int[4];
    for (int n = 1; n < 5; n++) {
      try {
        vals[n-1] = Integer.parseInt(json[n].trim());
      } catch (Exception ex) {
        vals[n-1] = 0;
      }
    }
    return new Rectangle(vals[0], vals[1], vals[2], vals[3]);
  }

  private static Point locFromJSON(String[] json) {
    int[] vals = new int[2];
    for (int n = 1; n < 3; n++) {
      try {
        vals[n-1] = Integer.parseInt(json[n].trim());
      } catch (Exception ex) {
        vals[n-1] = 0;
      }
    }
    return new Point(vals[0], vals[1]);
  }

  private static int intFromJSON(String[] json, int pos) {
    try {
      return Integer.parseInt(json[pos].trim());
    } catch (Exception ex) {
      return 0;
    }
  }

  private static float fltFromJSON(String[] json, int pos) {
    try {
      return Float.parseFloat(json[pos].trim());
    } catch (Exception ex) {
      return 0;
    }
  }

  private static double dblFromJSON(String[] json, int pos) {
    try {
      return Double.parseDouble(json[pos].trim());
    } catch (Exception ex) {
      return 0;
    }
  }

  private static void logCmd(String cmd, Object... args) {
		String msg = cmd + ": ";
		if (args.length == 0) {
			log.debug(msg + "no-args");
		} else {
			for (int i = 0; i < args.length; i++) {
				msg += "%s ";
			}
			log.debug(msg, args);
		}
	}
//</editor-fold>

}
