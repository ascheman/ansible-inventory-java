ansible-static-inventory
========================

A Java library to read and write [Ansible](https://www.ansible.com/) [static inventories](https://docs.ansible.com/ansible/intro_inventory.html).

This is a fork of https://gitlab.com/ilpianista/ansible-static-inventory.git.

## Modifications

* Added method to `AnsibleInventoryReader.read(File f)`.
* Add group-vars not only to hosts but also to group.
* Support values containing whitespaces (ie.: `foo="this is foo"`).
* Also support whitespaces between variable, "=" and value (e.g. `foo = bar` or `foo = "this is bar"`).
* Whitespace values can be enclosed by single or double quotes (or no quotes at all when in the vars section - see below).
* Furthermore, allow "=" signs within the value, e.g.: `foo = "this = foo"`.
* In the vars section Ansible allows whitespace values without quotes (e.g. `foo = this is foo`). We support this too.
* If you want to, you can even use things like `foo = this = foo =` (not saying, that this makes much sense, though).


## Build

    $ git clone https://github.com/rawbertp/ansible-inventory-java.git
    $ cd ansible-static-inventory
    $ mvn clean install

## License

MIT
