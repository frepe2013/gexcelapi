package org.jggug.commons.gexcelapi

import org.apache.poi.hssf.usermodel.*
import org.apache.poi.poifs.filesystem.*
import org.apache.poi.ss.usermodel.*
import static org.jggug.commons.gexcelapi.Util.*
import java.lang.IndexOutOfBoundsException as IOOBEx

class GExcel {
    static {
        expandHSSFWorkbook()
        expandHSSFSheet()
        expandHSSFRow()
        expandHSSFCell()
    }
    private static expandHSSFWorkbook() {
        HSSFWorkbook.metaClass.define {
            getAt { int idx -> delegate.getSheetAt(idx) }
            getProperty { String name -> delegate.getSheet(name) }
        }
    }
    private static expandHSSFSheet() {
        HSSFSheet.metaClass.define {
            getProperty { name ->
                if (name == "rows") return rows()
                if (name ==~ /_\d+/) return delegate.getRow(rowIndex(name))
                if (name ==~ /[a-zA-Z]+\d+/) {
                    try { delegate.getRow(rowIndex(name))?.getCell(colIndex(name)) } catch (IOOBEx e) { return null }
                }
            }
            setProperty { name, value ->
                if (name ==~ /[a-zA-Z]+\d+/) {
                    try { delegate.getRow(rowIndex(name))?.getCell(colIndex(name)).setCellValue(value) } catch (IOOBEx e) { return null }
                }
            }
            rows { delegate?.findAll{true} }
            validate { delegate.rows.every { row -> row.validate() } }
        }
    }
    private static expandHSSFRow() {
        HSSFRow.metaClass.define {
            getAt { int idx -> delegate.getCell(idx) }
            getProperty { name ->
                if (name ==~ /[a-zA-Z]+_/) return delegate.getCell(colIndex(name))
                null
            }
            validate { delegate.every { cell -> cell.validate() } }
        }
    }
    private static expandHSSFCell() {
        HSSFCell.metaClass.__validators__ = null
        HSSFCell.metaClass.define {
            isStringType  { delegate.cellType == Cell.CELL_TYPE_STRING }
            isNumericType { delegate.cellType == Cell.CELL_TYPE_NUMERIC }
            isBooleanType { delegate.cellType == Cell.CELL_TYPE_BOOLEAN }
            getValue {
                // implicitly accessing value by appropriate type
                switch(delegate.cellType) {
                    case Cell.CELL_TYPE_STRING:  return delegate.stringCellValue
                    case Cell.CELL_TYPE_NUMERIC: return delegate.numericCellValue
                    case Cell.CELL_TYPE_BOOLEAN: return delegate.booleanCellValue
                    default: throw new RuntimeException("unsupported cell type: ${delegate.cellType}")
                }
            }
            setValue { value -> delegate.setCellValue(value) }
            leftShift { value -> delegate.setCellValue(value) }
            asType { Class type ->
                // explicitly accessing value by appropriate type
                switch(type) {
                    case Double:  return delegate.numericCellValue
                    case Integer: return delegate.numericCellValue.intValue()
                    case Boolean: return delegate.booleanCellValue
                    case Date:    return delegate.dateCellValue
                    case String:  return new String(delegate.stringCellValue.getBytes("Windows-31J")) // Windows-31J -> default encoding
                    default: throw new RuntimeException("unsupported cell type: ${delegate.cellType}")
                }
            }
            toString { delegate.value as String }
            clearValidators {
                delegate.__validators__ = []
            }
            getValidators {
                if (delegate.__validators__ == null) { delegate.clearValidators() }
                delegate.__validators__
            }
            addValidator { Closure validator ->
                delegate.validators << validator
            }
            setValidator { Closure validator ->
                delegate.clearValidators()
                delegate.validators << validator
            }
            validate {
                delegate.validators.every { validator -> validator.call(delegate) }
            }
        }
    }

    static open(String file) { open(new File(file)) }
    static open(File file) { open(new FileInputStream(file)) }
    static open(InputStream is) { new HSSFWorkbook(new POIFSFileSystem(is)) }
}

