//
// Copyright (c) 2007 Tridium, Inc.
// Licensed under the Academic Free License version 3.0
//
// History:
//   2 Apr 07  Brian Frank  Creation
//

package sedonac.steps;

import java.io.*;
import java.util.*;

import sedona.*;
import sedona.kit.*;
import sedona.util.*;
import sedona.xml.*;
import sedonac.*;
import sedonac.Compiler;
import sedonac.ir.*;
import sedonac.platform.*;
import sedonac.util.VarResolver;

/**
 * InitStagePlatform uses the platform definition to load the kits
 */
public class InitStagePlatform
  extends InitImageCompile
{

//////////////////////////////////////////////////////////////////////////
// Run
//////////////////////////////////////////////////////////////////////////

  public InitStagePlatform(Compiler compiler)
  {
    super(compiler);
  }

  public void run()
  {
    try
    {
      log.info("  InitStagePlatform [" + xmlFile.getName() + "]");
      
      // check args
      if (compiler.outDir == null)
        throw err("Must specify target directory via -outDir", new Location("compiler args"));
      
      // create staging directories
      makeStageDirs();

      PlatformDef plat = compiler.platform = parsePlatformDef();

      // initialize kits                         
      IrKit[] kits = new IrKit[plat.nativeKits.length];
      for (int i=0; i<kits.length; ++i)
      {                                   
        Depend depend = plat.nativeKits[i];
        KitFile kitFile = KitDb.matchBest(depend);
        if (kitFile == null)
        {
          err("Missing kit dependency '" + depend + "'", new Location(xml));
          continue;
        }        
        kits[i] = new IrKit(new Location(xml), kitFile);
      }
      compiler.kits = kits;
      quitIfErrors();
    }
    catch (XException e)
    {
      throw err(e);
    }
  }
  
//////////////////////////////////////////////////////////////////////////
// Make Dirs
//////////////////////////////////////////////////////////////////////////
  
  private void makeStageDirs()
  {
    // nuke it first to get a clean slate
    try
    {
      FileUtil.delete(compiler.outDir, log);
    }
    catch (IOException e)
    {
      err("Cannot delete stage dir", new Location(compiler.outDir));
    }
    
    // create stage directories
    // <stageDir>/.par/
    //              +- /svm
    //
    File dir = null;
    try
    {
      // makes all directories in the path
      dir = new File(new File(compiler.outDir, ".par"), "svm");
      FileUtil.mkdir(dir, log);
    }
    catch (IOException e)
    {
      err("Cannot make stage dir", new Location(dir));
    }
  }
  
//////////////////////////////////////////////////////////////////////////
// PlatformDef
//////////////////////////////////////////////////////////////////////////

  private PlatformDef parsePlatformDef()
  {
    PlatformDef plat = new PlatformDef();
    
    plat.vendor     = xml.get("vendor");
    plat.idPattern  = xml.get("id", null);
    
    parseCompile(plat, xml.elem("compile", true));
    parseManifestIncludes(plat, xml.elems("manifestInclude"));
    
    checkVendor(plat);
    return plat;
  }
  
  private void parseCompile(PlatformDef plat, XElem xml)
  {
    VarResolver vars = new VarResolver();
    plat.refSize   = xml.geti("refSize");
    plat.blockSize = xml.geti("blockSize");
    plat.endian    = xml.get("endian");
    plat.armDouble = xml.getb("armDouble", plat.armDouble);
    plat.debug     = xml.getb("debug", plat.debug);
    plat.test      = xml.getb("test", plat.test);
    
    XElem[] x = xml.elems("nativeKit");
    Depend[] kits = new Depend[x.length];
    for (int i=0; i<x.length; ++i)
      kits[i] = x[i].getDepend("depend");
    plat.nativeKits = kits;
      
    x = xml.elems("nativeSource");
    String[] paths = new String[x.length];
    try
    {
      for (int i=0; i<x.length; ++i)
        paths[i] = vars.resolve(x[i].get("path"));
      plat.nativePaths = paths;
    }
    catch (Exception e) { throw err(e.getMessage()); }
    
    x = xml.elems("nativePatch");
    String[] slots  = new String[x.length];
    for (int i=0; i<x.length; ++i)
      slots[i] = x[i].get("qname");
    plat.nativePatches = slots;
    
    // Sanity checks.
    if (!"big".equals(plat.endian) && !"little".equals(plat.endian))
      throw new XException("compile.endian must be 'big' or 'little'", xml);
    
    if (plat.nativeKits == null || plat.nativeKits.length == 0) 
      throw new XException("no compile.nativeKit elements defined", xml);
    
    if (xml.elems("include").length > 0)
      throw new XException("<include> is no longer supported.", xml);
  }
  
  private void parseManifestIncludes(PlatformDef plat, XElem[] includes)
  {
    ArrayList includeElements = new ArrayList();
    for (int i=0; i<includes.length; ++i)
    {
      String path = includes[i].get("path", null);
      if (path != null)
        includeElements.add(resolveManifestInclude(path));
      
      XElem[] children = includes[i].elems();
      for (int j=0; j<children.length; ++j)
      {
        includeElements.add(children[j]);
        includes[i].removeContent(children[j]);
      }
    }
    
    plat.manifestIncludes = (XElem[])includeElements.toArray(new XElem[includeElements.size()]);
  }
  
  private XElem resolveManifestInclude(String path)
  {
    Location loc = new Location(xmlFile);
    File include = null;
    if (!path.startsWith("/"))
      include = new File(xmlDir, path);
    else
      include  = new File(Env.home, path.substring(1));
    
    try
    {
      return XParser.make(include).parse();
    }
    catch (Exception e)
    {
      throw err("Could not include '" + path + "'", loc, e);
    }
  }
  
  private void checkVendor(PlatformDef plat)
  {
    Location loc = new Location(xmlFile);
    try
    {
      VendorUtil.checkVendorName(plat.vendor);
      VendorUtil.checkPlatformPrefix(plat.vendor, plat.idPattern);
    }
    catch (Exception e)
    {
      throw err(e.getMessage(), loc);
    }
  }

}
